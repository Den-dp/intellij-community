// "Add 'open fun f(): Int' to 'A'" "true"
// WITH_STDLIB
open class A {
}
class B : A() {
    <caret>override fun f(): Int = 5
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddFunctionToSupertypeFix
// IGNORE_K2