package io.provenance.aggregate.service.stream.templates

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.apache.commons.text.StringSubstitutor
import org.apache.commons.text.io.StringSubstitutorReader
import java.io.File
import java.io.FileReader
import java.net.URL

class MissingTemplate(name: String) : Exception("Template $name not found")
class MissingTemplateDirectory(name: String) : Exception("Template directory $name not found")

fun read(filename: String, vars: Map<String, Any> = emptyMap()): String {
    // HACK: Since this is a top-level function not in an explicit class, use the class of object:
    val file = try {
        object {}.javaClass.classLoader.getResource("templates/" + filename).file
    } catch (e: Exception) {
        throw MissingTemplate(filename)
    }
    return StringSubstitutorReader(FileReader(file), StringSubstitutor(vars)).readText()
}

fun readAll(directory: String, vars: Map<String, Any> = emptyMap()): Sequence<String> {
    val url: URL = try {
        object {}.javaClass.classLoader.getResource("templates/$directory")
    } catch (e: Exception) {
        throw MissingTemplateDirectory(directory)
    }
    val base = File(url.toURI())
    return sequence {
        base.walk().forEach {
            val filename = it.toRelativeString(base)
            if (!filename.isNullOrEmpty()) {
                yield(read("$directory/$filename", vars))
            }
        }
    }
}

@OptIn(ExperimentalStdlibApi::class)
inline fun <reified T : Any> readAs(filename: String, vars: Map<String, Any> = emptyMap()): T? {
    val contents: String = read(filename, vars)
    val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()
    val adapter: JsonAdapter<T> = moshi.adapter(T::class.java)
    return adapter.fromJson(contents)
}

@OptIn(ExperimentalStdlibApi::class)
inline fun <reified T : Any> unsafeReadAs(filename: String, vars: Map<String, Any> = emptyMap()): T =
    readAs(filename, vars)!!
