// PSI_ELEMENT: org.jetbrains.kotlin.psi.JetObjectDeclaration
// OPTIONS: usages
fun foo(): Any {
    object <caret>Bar

    return Bar
}

val x = Bar
