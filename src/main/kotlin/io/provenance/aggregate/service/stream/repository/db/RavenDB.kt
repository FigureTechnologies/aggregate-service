package io.provenance.aggregate.service.stream.repository.db

import arrow.core.right
import arrow.core.rightIfNull
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.provenance.aggregate.service.stream.extractors.csv.impl.AttributesDB
import io.provenance.aggregate.service.stream.extractors.csv.impl.CoinTransferDB
import io.provenance.aggregate.service.stream.extractors.csv.impl.ErrorsDB
import io.provenance.aggregate.service.stream.extractors.csv.impl.MarkerSupplyDB
import io.provenance.aggregate.service.stream.extractors.csv.impl.MarkerTransferDB
import io.provenance.aggregate.service.stream.extractors.csv.impl.MemorializeContractDB
import net.ravendb.client.documents.DocumentStore
import net.ravendb.client.documents.session.IDocumentSession
import java.util.Objects
import kotlin.reflect.jvm.internal.impl.load.kotlin.JvmType.Object

class RavenDB(
    private val addr: String?,
    private val dbName: String?
): DBInterface<Any> {

    val store = DocumentStore(addr, dbName).also { it.initialize() }
    val session: IDocumentSession = store.openSession()
    val kMapper: ObjectMapper = ObjectMapper().registerModule(KotlinModule())

    inline fun <reified T: Any> Any.cast(): T { return this as T }

//    private fun kObjectMapper(vararg data: Any?): Any? {
//        val dataValue = data.map { obj -> obj.
//            println(obj.)
//        }
//        //val dataValue2 = dataValue
//        println(dataValue)
//
//        return null;
////        return if(dataValue is CoinTransferDB) {
////            kMapper.convertValue(dataValue, CoinTransferDB::class.java)
////        } else if(dataValue is MarkerTransferDB) {
////            kMapper.convertValue(dataValue, MarkerTransferDB::class.java)
////        }else if(dataValue is MarkerSupplyDB) {
////            kMapper.convertValue(dataValue, MarkerSupplyDB::class.java)
////        }else if(dataValue is MemorializeContractDB) {
////            kMapper.convertValue(dataValue, MemorializeContractDB::class.java)
////        }else if(dataValue is ErrorsDB) {
////            kMapper.convertValue(dataValue, ErrorsDB::class.java)
////        }else if(dataValue is AttributesDB) {
////            kMapper.convertValue(dataValue, AttributesDB::class.java)
////        } else {
////            null
////        }
//    }
//        when(val dataValue = data.first()) {
//            is CoinTransferDB -> kMapper.convertValue(dataValue, CoinTransferDB::class.java)
//            is MarkerTransferDB -> kMapper.convertValue(dataValue, MarkerTransferDB::class.java)
//            is MarkerSupplyDB -> kMapper.convertValue(dataValue, MarkerSupplyDB::class.java)
//            is MemorializeContractDB -> kMapper.convertValue(dataValue, MemorializeContractDB::class.java)
//            is ErrorsDB -> kMapper.convertValue(dataValue, ErrorsDB::class.java)
//            is AttributesDB -> kMapper.convertValue(dataValue, AttributesDB::class.java)
//            else -> null
//        }

    override fun save(hash: String?, vararg data: Any?) {
        synchronized(this) {
            val saveMe = when (val dataValue = data.first()) {
                is CoinTransferDB -> kMapper.convertValue(dataValue, CoinTransferDB::class.java)
                is MarkerTransferDB -> kMapper.convertValue(dataValue, MarkerTransferDB::class.java)
                is MarkerSupplyDB -> kMapper.convertValue(dataValue, MarkerSupplyDB::class.java)
                is MemorializeContractDB -> kMapper.convertValue(dataValue, MemorializeContractDB::class.java)
                is ErrorsDB -> kMapper.convertValue(dataValue, ErrorsDB::class.java)
                is AttributesDB -> kMapper.convertValue(dataValue, AttributesDB::class.java)
                else -> null
            }
            session.store(saveMe, hash)
        }
    }

    override fun saveChanges() {
        session.saveChanges()
    }

}
