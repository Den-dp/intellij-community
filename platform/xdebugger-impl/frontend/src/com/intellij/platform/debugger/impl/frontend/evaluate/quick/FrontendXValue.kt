// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend.evaluate.quick

import com.intellij.ide.ui.icons.icon
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.util.coroutines.childScope
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.ConcurrencyUtil
import com.intellij.xdebugger.Obsolescent
import com.intellij.xdebugger.frame.XCompositeNode
import com.intellij.xdebugger.frame.XValue
import com.intellij.xdebugger.frame.XValueChildrenList
import com.intellij.xdebugger.frame.XValueModifier
import com.intellij.xdebugger.frame.XValueNode
import com.intellij.xdebugger.frame.XValuePlace
import com.intellij.xdebugger.frame.presentation.XValuePresentation
import com.intellij.xdebugger.impl.evaluate.quick.HintXValue
import com.intellij.xdebugger.impl.rpc.XDebuggerEvaluatorApi
import com.intellij.xdebugger.impl.rpc.XValueAdvancedPresentationPart
import com.intellij.xdebugger.impl.rpc.XValueComputeChildrenEvent
import com.intellij.xdebugger.impl.rpc.XValueDto
import com.intellij.xdebugger.impl.rpc.XValuePresentationEvent
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeEx
import kotlinx.coroutines.*

internal class FrontendXValue(private val project: Project, private val xValueDto: XValueDto) : XValue(), HintXValue {
  // TODO[IJPL-160146]: For evaluation in toolwindow this CoroutineScope won't be cancelled
  private val cs = project.service<FrontendXValueDisposer>().cs.childScope("FrontendXValue")

  @Volatile
  private var modifier: XValueModifier? = null

  init {
    cs.launch {
      val canBeModified = xValueDto.canBeModified.await()
      if (canBeModified) {
        modifier = FrontendXValueModifier(project, xValueDto)
      }
    }
  }

  override fun computePresentation(node: XValueNode, place: XValuePlace) {
    node.childCoroutineScope(parentScope = cs, "FrontendXValue#computePresentation").launch(Dispatchers.EDT) {
      XDebuggerEvaluatorApi.getInstance().computePresentation(xValueDto.id, place)?.collect { presentationEvent ->
        when (presentationEvent) {
          is XValuePresentationEvent.SetSimplePresentation -> {
            node.setPresentation(presentationEvent.icon?.icon(), presentationEvent.presentationType, presentationEvent.value, presentationEvent.hasChildren)
          }
          is XValuePresentationEvent.SetAdvancedPresentation -> {
            node.setPresentation(presentationEvent.icon?.icon(), FrontendXValuePresentation(presentationEvent), presentationEvent.hasChildren)
          }
          is XValuePresentationEvent.SetFullValueEvaluator -> {
            node.setFullValueEvaluator(FrontendXFullValueEvaluator(cs, presentationEvent.fullValueEvaluatorDto))
          }
          XValuePresentationEvent.ClearFullValueEvaluator -> {
            if (node is XValueNodeEx) {
              node.clearFullValueEvaluator()
            }
          }
        }
      }
    }
  }

  override fun computeChildren(node: XCompositeNode) {
    node.childCoroutineScope(parentScope = cs, "FrontendXValue#computeChildren").launch(Dispatchers.EDT) {
      XDebuggerEvaluatorApi.getInstance().computeChildren(xValueDto.id)?.collect { computeChildrenEvent ->
        when (computeChildrenEvent) {
          is XValueComputeChildrenEvent.AddChildren -> {
            val childrenList = XValueChildrenList()
            for (i in computeChildrenEvent.children.indices) {
              childrenList.add(computeChildrenEvent.names[i], FrontendXValue(project, computeChildrenEvent.children[i]))
            }
            node.addChildren(childrenList, computeChildrenEvent.isLast)
          }
          is XValueComputeChildrenEvent.SetAlreadySorted -> {
            node.setAlreadySorted(computeChildrenEvent.value)
          }
          is XValueComputeChildrenEvent.SetErrorMessage -> {
            node.setErrorMessage(computeChildrenEvent.message, computeChildrenEvent.link)
          }
          is XValueComputeChildrenEvent.SetMessage -> {
            // TODO[IJPL-160146]: support SimpleTextAttributes serialization -- don't pass SimpleTextAttributes.REGULAR_ATTRIBUTES
            node.setMessage(
              computeChildrenEvent.message,
              computeChildrenEvent.icon?.icon(),
              computeChildrenEvent.attributes ?: SimpleTextAttributes.REGULAR_ATTRIBUTES,
              computeChildrenEvent.link
            )
          }
          is XValueComputeChildrenEvent.TooManyChildren -> {
            val addNextChildren = computeChildrenEvent.addNextChildren
            if (addNextChildren != null) {
              node.tooManyChildren(computeChildrenEvent.remaining, Runnable { addNextChildren.trySend(Unit) })
            }
            else {
              @Suppress("DEPRECATION")
              node.tooManyChildren(computeChildrenEvent.remaining)
            }
          }
        }
      }
    }
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  override fun getModifier(): XValueModifier? {
    return modifier
  }

  override fun dispose() {
    project.service<FrontendXValueDisposer>().dispose(xValueDto)
    cs.cancel()
  }

  private class FrontendXValuePresentation(private val advancedPresentation: XValuePresentationEvent.SetAdvancedPresentation) : XValuePresentation() {
    override fun renderValue(renderer: XValueTextRenderer) {
      for (part in advancedPresentation.parts) {
        when (part) {
          is XValueAdvancedPresentationPart.Comment -> {
            renderer.renderComment(part.comment)
          }
          is XValueAdvancedPresentationPart.Error -> {
            renderer.renderError(part.error)
          }
          is XValueAdvancedPresentationPart.KeywordValue -> {
            renderer.renderKeywordValue(part.value)
          }
          is XValueAdvancedPresentationPart.NumericValue -> {
            renderer.renderNumericValue(part.value)
          }
          is XValueAdvancedPresentationPart.SpecialSymbol -> {
            renderer.renderSpecialSymbol(part.symbol)
          }
          is XValueAdvancedPresentationPart.StringValue -> {
            renderer.renderStringValue(part.value)
          }
          is XValueAdvancedPresentationPart.StringValueWithHighlighting -> {
            renderer.renderStringValue(part.value, part.additionalSpecialCharsToHighlight, part.maxLength)
          }
          is XValueAdvancedPresentationPart.Value -> {
            renderer.renderValue(part.value)
          }
          is XValueAdvancedPresentationPart.ValueWithAttributes -> {
            // TODO[IJPL-160146]: support [TextAttributesKey] serialization
            val attributesKey = part.key
            if (attributesKey != null) {
              renderer.renderValue(part.value, attributesKey)
            }
            else {
              renderer.renderValue(part.value)
            }
          }
        }
      }
    }

    override fun getSeparator(): @NlsSafe String {
      return advancedPresentation.separator
    }

    override fun isShowName(): Boolean {
      return advancedPresentation.isShownName
    }

    override fun getType(): @NlsSafe String? {
      return advancedPresentation.presentationType
    }

    override fun isAsync(): Boolean {
      return advancedPresentation.isAsync
    }
  }
}

internal fun Obsolescent.childCoroutineScope(parentScope: CoroutineScope, name: String): CoroutineScope {
  val obsolescent = this
  val scope = parentScope.childScope(name)
  parentScope.launch(context = Dispatchers.IO, start = CoroutineStart.UNDISPATCHED) {
    while (!obsolescent.isObsolete) {
      delay(ConcurrencyUtil.DEFAULT_TIMEOUT_MS)
    }
    scope.cancel()
  }
  return scope
}

@Service(Service.Level.PROJECT)
private class FrontendXValueDisposer(project: Project, val cs: CoroutineScope) {
  fun dispose(xValueDto: XValueDto) {
    cs.launch(Dispatchers.IO) {
      XDebuggerEvaluatorApi.getInstance().disposeXValue(xValueDto.id)
    }
  }
}