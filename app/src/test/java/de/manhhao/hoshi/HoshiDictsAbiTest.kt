package de.manhhao.hoshi

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HoshiDictsAbiTest {
    // Fork: the kaihouguide JNI engine constructs ImportResult/PitchEntry/LookupResult with its
    // own shapes; upstream's complete-pitch and kanji models are plain adapters on top. Guard the
    // JNI constructors so a merge cannot silently change them.
    @Test
    fun typedModelsExposeCompletePitchAndKanjiAbi() {
        assertHasConstructor(
            className = "de.manhhao.hoshi.ImportResult",
            Boolean::class.javaPrimitiveType!!,
            String::class.java,
            Long::class.javaPrimitiveType!!,
            Long::class.javaPrimitiveType!!,
            Long::class.javaPrimitiveType!!,
            Long::class.javaPrimitiveType!!,
            Long::class.javaPrimitiveType!!,
        )
        assertConstructor(
            className = "de.manhhao.hoshi.Pitch",
            Int::class.javaPrimitiveType!!,
            String::class.java,
            IntArray::class.java,
            IntArray::class.java,
        )
        assertHasConstructor(
            className = "de.manhhao.hoshi.PitchEntry",
            String::class.java,
            IntArray::class.java,
        )
        assertHasConstructor(
            className = "de.manhhao.hoshi.PitchEntry",
            String::class.java,
            arrayClass("de.manhhao.hoshi.Pitch"),
            Array<String>::class.java,
        )
        assertConstructor(
            className = "de.manhhao.hoshi.LookupResult",
            String::class.java,
            String::class.java,
            arrayClass("de.manhhao.hoshi.TransformGroup"),
            loadClass("de.manhhao.hoshi.TermResult"),
            Int::class.javaPrimitiveType!!,
        )
        assertConstructor(
            className = "de.manhhao.hoshi.KanjiStat",
            String::class.java,
            String::class.java,
        )
        assertConstructor(
            className = "de.manhhao.hoshi.KanjiEntry",
            String::class.java,
            String::class.java,
            String::class.java,
            String::class.java,
            Array<String>::class.java,
            arrayClass("de.manhhao.hoshi.KanjiStat"),
        )
        assertConstructor(
            className = "de.manhhao.hoshi.KanjiResult",
            String::class.java,
            arrayClass("de.manhhao.hoshi.KanjiEntry"),
        )
    }

    @Test
    fun nativeMethodsExposeKanjiSessionAbi() {
        val bridgeClass = loadClass("de.manhhao.hoshi.HoshiDicts")
        val rebuild = bridgeClass.getDeclaredMethod(
            "rebuildQuery",
            Long::class.javaPrimitiveType,
            Array<String>::class.java,
            Array<String>::class.java,
            Array<String>::class.java,
            Array<String>::class.java,
        )
        assertEquals(Void.TYPE, rebuild.returnType)

        val queryKanji = bridgeClass.getDeclaredMethod(
            "queryKanji",
            Long::class.javaPrimitiveType,
            String::class.java,
        )
        assertEquals(loadClass("de.manhhao.hoshi.KanjiResult"), queryKanji.returnType)
    }

    private fun assertConstructor(className: String, vararg parameterTypes: Class<*>) {
        val constructors = loadClass(className).declaredConstructors
        assertEquals("Expected exactly one constructor for $className", 1, constructors.size)
        assertArrayEquals(parameterTypes, constructors.single().parameterTypes)
    }

    private fun assertHasConstructor(className: String, vararg parameterTypes: Class<*>) {
        val matches = loadClass(className).declaredConstructors.any { it.parameterTypes.contentEquals(parameterTypes) }
        assertTrue("Expected a constructor for $className with ${parameterTypes.map { it.simpleName }}", matches)
    }

    private fun arrayClass(componentClassName: String): Class<*> =
        java.lang.reflect.Array.newInstance(loadClass(componentClassName), 0).javaClass

    private fun loadClass(name: String): Class<*> =
        Class.forName(name, false, checkNotNull(javaClass.classLoader))
}
