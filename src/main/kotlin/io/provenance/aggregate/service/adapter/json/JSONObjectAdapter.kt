package io.provenance.aggregate.service.adapter.json

import com.squareup.moshi.*
import okio.Buffer
import org.json.JSONObject

class JSONObjectAdapter : JsonAdapter<JSONObject>() {

    @FromJson
    override fun fromJson(reader: JsonReader): JSONObject? {
        val data = reader.readJsonValue() as? Map<String, Any>
        return data?.let {
            JSONObject(it)
        }
    }

    @ToJson
    override fun toJson(writer: JsonWriter, value: JSONObject?) {
        value?.let {
            val b = Buffer().writeUtf8(value.toString())
            writer.jsonValue(b)
        }
    }
}