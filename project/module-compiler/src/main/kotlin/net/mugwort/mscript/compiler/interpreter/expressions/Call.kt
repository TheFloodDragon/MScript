package net.mugwort.mscript.compiler.interpreter.expressions

import net.mugwort.mscript.compiler.interpreter.Interpreter
import net.mugwort.mscript.compiler.interpreter.statements.classes.core.Class
import net.mugwort.mscript.compiler.interpreter.statements.classes.core.NativeClass
import net.mugwort.mscript.compiler.interpreter.statements.function.FunctionStatement
import net.mugwort.mscript.compiler.interpreter.statements.function.core.Function
import net.mugwort.mscript.compiler.interpreter.statements.function.core.NativeFunction
import net.mugwort.mscript.core.ast.core.Expression
import net.mugwort.mscript.core.ast.core.Statement
import net.mugwort.mscript.runtime.Environment
import net.mugwort.mscript.runtime.expection.thrower

class Call(private val interpreter: Interpreter? = null) : ExpressionExecutor() {
    override val self: ExpressionExecutor = this

    override fun visit(body: Expression, env: Environment?): Any? {
        val expr = body as Expression.CallExpression
        val params = arrayListOf<Any?>()
        for (param in expr.arguments) {
            if (env != null) {
                params.add(executor(param, env,interpreter))
            } else {
                params.add(executor(param,null,interpreter))
            }
        }
        return caller(expr.caller.name, params, env)
    }

    fun caller(calls: String, params: List<Any?>, env: Environment?): Any? {
        if (interpreter != null){
            for (statement in interpreter.program.body) {
                if (statement is Statement.FunctionDeclaration) {
                    if (statement.identifier.name == calls) {
                        if (env?.get(calls) == null) {
                           FunctionStatement(interpreter).newFunction(statement, env)
                        }
                    }
                }
            }
        }
        val caller = env?.get(calls) ?: thrower.RuntimeException("Cannot Found $calls")
        when (caller) {
            is Class -> {
                return caller.call(params)
            }
            is NativeClass ->{
                return caller.call(params)
            }
            is NativeFunction -> {
                return caller.call(params)
            }

            is Function -> {
                return caller.call(params)
            }

            else -> {
                thrower.RuntimeException("The Variable $calls isn`t Function")
            }
        }
        return null
    }
}