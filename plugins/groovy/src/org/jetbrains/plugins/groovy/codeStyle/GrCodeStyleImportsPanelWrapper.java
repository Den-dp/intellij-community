// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.codeStyle;

import com.intellij.application.options.CodeStyleAbstractPanel;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.util.NlsContexts.TabTitle;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author Max Medvedev
 */
public class GrCodeStyleImportsPanelWrapper extends CodeStyleAbstractPanel {

  private final GrCodeStyleImportsPanel myImportsPanel;

  protected GrCodeStyleImportsPanelWrapper(CodeStyleSettings settings) {
    super(settings);
    myImportsPanel = new GrCodeStyleImportsPanel();
  }


  @Override
  protected int getRightMargin() {
    return 0;
  }

  @Override
  protected EditorHighlighter createHighlighter(@NotNull EditorColorsScheme scheme) {
    return null;
  }

  @Override
  protected @NotNull FileType getFileType() {
    return JavaFileType.INSTANCE;
  }

  @Override
  protected String getPreviewText() {
    return null;
  }

  @Override
  public void apply(@NotNull CodeStyleSettings settings) {
    myImportsPanel.apply(settings);
  }

  @Override
  public boolean isModified(CodeStyleSettings settings) {
    return myImportsPanel.isModified(settings);
  }

  @Override
  public JComponent getPanel() {
    return myImportsPanel;
  }

  @Override
  protected void resetImpl(@NotNull CodeStyleSettings settings) {
    myImportsPanel.reset(settings);
  }

  @Override
  protected @TabTitle @NotNull String getTabTitle() {
    return ApplicationBundle.message("title.imports");
  }
}
