package org.junit
@Target(AnnotationTarget.FUNCTION) @Retention(AnnotationRetention.RUNTIME) annotation class Test
object Assert {
    @JvmStatic fun assertEquals(a: Any?, b: Any?) { if (a != b) throw AssertionError("expected <$a> but was <$b>") }
    @JvmStatic fun assertEquals(msg: String, a: Any?, b: Any?) { if (a != b) throw AssertionError("$msg: expected <$a> but was <$b>") }
    @JvmStatic fun assertEquals(a: Double, b: Double, eps: Double) { if (Math.abs(a - b) > eps) throw AssertionError("expected <$a> but was <$b>") }
    @JvmStatic fun assertEquals(msg: String, a: Double, b: Double, eps: Double) { if (Math.abs(a - b) > eps) throw AssertionError("$msg: expected <$a> but was <$b>") }
    @JvmStatic fun assertEquals(a: Float, b: Float, eps: Float) { if (Math.abs(a - b) > eps) throw AssertionError("expected <$a> but was <$b>") }
    @JvmStatic fun assertTrue(c: Boolean) { if (!c) throw AssertionError("assertion failed") }
    @JvmStatic fun assertTrue(msg: String, c: Boolean) { if (!c) throw AssertionError(msg) }
}
