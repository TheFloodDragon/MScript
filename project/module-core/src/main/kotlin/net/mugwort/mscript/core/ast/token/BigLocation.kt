package net.mugwort.mscript.core.ast.token

class BigLocation(private val start : Location, private val end: Location){
    fun toMap(): Map<String, Map<String, Int>> {
        return mapOf("start" to start.toMap(), "end" to end.toMap())
    }

    override fun toString(): String {
        return  mapOf("start" to start.toMap(), "column" to end.toMap()).toString()
    }
}