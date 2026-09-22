import org.junit.Test
fun main() {
    val cls = Class.forName("com.tracelens.app.core.CoreTest")
    var pass = 0; var fail = 0
    for (m in cls.declaredMethods.sortedBy { it.name }) {
        if (m.getAnnotation(Test::class.java) == null) continue
        try { m.invoke(cls.getDeclaredConstructor().newInstance()); pass++; println("PASS ${m.name}") }
        catch (e: java.lang.reflect.InvocationTargetException) { fail++; println("FAIL ${m.name}: ${e.targetException.message}") }
    }
    println("== $pass passed, $fail failed"); if (fail > 0) System.exit(1)
}
