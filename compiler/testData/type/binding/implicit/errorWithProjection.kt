fun <T> getT(): T = null!!

val foo = getT<List<in Int, out Int>>()
/*
psi: val foo = getT<List<in Int, out Int>>()
type: [ERROR : List<in Int, out Int>]
*/