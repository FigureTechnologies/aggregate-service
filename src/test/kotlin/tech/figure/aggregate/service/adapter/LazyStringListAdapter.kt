package tech.figure.aggregate.service.adapter

import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import com.google.protobuf.LazyStringArrayList
import com.google.protobuf.LazyStringList

class LazyStringListAdapter: TypeAdapter<LazyStringList>() {

    override fun write(jsonWriter: JsonWriter?, value: LazyStringList?) {
        TODO("Not yet implemented")
    }

    override fun read(jsonReader: JsonReader?): LazyStringList {
        val lazyStringList = LazyStringArrayList()

        jsonReader?.beginArray()

        jsonReader?.let {
            while(jsonReader.hasNext()) {
                lazyStringList.add(jsonReader.nextString())
            }
        }

        jsonReader?.endArray()

        return lazyStringList
    }
}
