// !DIAGNOSTICS: -UNUSED_PARAMETER

trait Foo<T> {
    fun foo(l: List<T>)
}

class Bar(f: Foo<String>): Foo<String> by f {
    <!CONFLICTING_JVM_DECLARATIONS!>fun foo(l: List<Int>)<!> {}
}