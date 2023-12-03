package net.mugwort.mscript.runtime.expection

import net.mugwort.mscript.runtime.Translation
import net.mugwort.mscript.utils.Logger
import kotlin.system.exitProcess


open class IException(val id : String) {
    companion object{
        fun send(id : String,message : String){
            Logger.fatal("${Translation.ExceptionOf.get()} $id : $message")
            exitProcess(0)
        }
    }


    fun send(message: String) {
        send(id, message)
    }
}