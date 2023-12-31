package net.mugwort.mscript.compiler

import net.mugwort.mscript.core.ast.core.Expression
import net.mugwort.mscript.core.ast.core.Statement
import net.mugwort.mscript.core.ast.token.BigLocation
import net.mugwort.mscript.core.ast.token.Location
import net.mugwort.mscript.core.ast.token.Token
import net.mugwort.mscript.core.ast.token.TokenType
import net.mugwort.mscript.runtime.expection.thrower
import net.mugwort.mscript.utils.JsonUtils
import java.io.File

class Parser(code : String,val file : File) {
    private var index = 0
    private val tokens = Lexer(code).tokens
    private var currentToken = tokens[index]
    private var isEnd = currentToken.type == TokenType.EOF || index >= tokens.size
    private val statementList : MutableList<Statement> = mutableListOf()
    private val expr = expression()
    private val state = statement()
    private var line = currentToken.location.line
    private var column = currentToken.location.column


    fun parser(): Statement.Program {
        while (!isEnd){
            state.get()?.let { statementList.add(it) }
        }
        val end = Location(line + 1,1)
        return Statement.Program(statementList, BigLocation(Location(1,1),end))
    }

    fun parserJson(): String? {
        return JsonUtils.toJson(parser().toMap())
    }


    private inner class statement{
        fun get() : Statement?{
            return when(currentToken.type){
                TokenType.VAR -> varStatement()
                TokenType.CONST,TokenType.VAL -> varStatement(true)
                TokenType.LEFT_BRACE -> blockStatement()
                TokenType.RETURN -> returnStatement()
                TokenType.WHILE -> whileStatement()
                TokenType.DO -> doWhileStatement()
                TokenType.CLASS -> classStatement()
                TokenType.IF -> ifStatement()
                TokenType.TRY -> tryStatement()
                TokenType.PRIVATE,TokenType.PUBLIC,TokenType.PROTECTED,TokenType.ALREADY -> visitorStatement()
                TokenType.ENUM -> enumStatement()
                TokenType.EVENT -> eventStatement()
                TokenType.INCLUDE -> importStatement()
                TokenType.SWITCH -> switchStatement()
                TokenType.DEF -> functionStatement()
                TokenType.SEMICOLON -> Statement.EmptyStatement()
                TokenType.IDENTIFIER -> {
                    when (peek().type) {
                        TokenType.LEFT_PAREN -> {
                            var ret = Statement.ExpressionStatement(expr.callee())
                            if (currentToken.type == TokenType.DOT){
                                ret = Statement.ExpressionStatement(expr.member(ret.expression))
                                spilt()
                                return ret
                            }
                            ret
                        }
                        TokenType.DOT, TokenType.LEFT_SQUARE -> Statement.ExpressionStatement(expr.member())

                        TokenType.Incrementing, TokenType.Subtraction -> {
                            expr.unary()?.let { Statement.ExpressionStatement(it) }
                        }

                        else -> {
                            if (expr.logical.contains(peek().type)){
                                Statement.ExpressionStatement(expr.logical(expr.get()))
                            }else if(expr.binary.contains(peek().type)) {
                                Statement.ExpressionStatement(expr.binary(expr.get()))
                            }else{
                                Statement.ExpressionStatement(expr.assignment())
                            }
                        }
                    }
                }
                TokenType.MINUS,TokenType.BANG,TokenType.Incrementing,TokenType.Subtraction -> expr.unary()?.let { Statement.ExpressionStatement(it) }
                TokenType.NEWLINE -> {
                    consume(TokenType.NEWLINE)
                    return null
                }
                else -> {
                    return if (currentToken.type != TokenType.EOF) {
                        Statement.ExpressionStatement(expr.get())
                    } else {
                        null
                    }
                }
            }
        }



        fun enumStatement(): Statement.EnumStatement {
            val start = Location(line,column)
            consume(TokenType.ENUM)
            val id = expression().identifier()
            val enums = ArrayList<Expression>()
            consume(TokenType.LEFT_BRACE)
            while (currentToken.type != TokenType.RIGHT_BRACE){
                if (currentToken.type == TokenType.COMMA){
                    consume(TokenType.COMMA)
                }
                when(currentToken.type){
                    TokenType.IDENTIFIER -> {
                        if (expr.complex.contains(peek().type) ||  check(TokenType.EQUAL)){
                            val left = expr.get()
                            var op = ""
                            if (!expr.complex.contains(currentToken.type)){
                                op = expr.isAssignmentOperator().value
                            }else{
                                thrower.send("Assignment in Enumerations are only accepted EQUAL","Enumerations Error",file,currentToken)
                            }
                            val right = expr.get()
                            enums.add(Expression.AssignmentExpression(left,op,right))
                        }else if (!check(TokenType.RIGHT_BRACE) || !check(TokenType.COMMA)){
                            thrower.send("Enumerations are only accepted Assignment and Identifier","Enumerations Error",file,currentToken)
                        }else{
                            enums.add(expr.identifier())
                        }
                    }
                    TokenType.NEWLINE -> {
                        spilt()
                    }
                    else -> {
                        thrower.send("Enumerations are only accepted Assignment and Identifier","Enumerations Error",file,currentToken)
                    }
                }
                if (currentToken.type == TokenType.RIGHT_BRACE) break
            }
            consume(TokenType.RIGHT_BRACE)
            val end = Location(line,column)
            return Statement.EnumStatement(id,enums, BigLocation(start,end))
        }

        fun visitorStatement(): Statement.VisitorStatement {
            val visitor = when(currentToken.type) {
                TokenType.PUBLIC -> {
                    consume(currentToken.type)
                    Statement.VisitorType.PUBLIC
                }
                TokenType.ALREADY -> {
                    consume(currentToken.type)
                    Statement.VisitorType.ALREADY
                }
                TokenType.PROTECTED -> {
                    consume(currentToken.type)
                    Statement.VisitorType.PROTECTED
                }
                TokenType.PRIVATE -> {
                    consume(currentToken.type)
                    Statement.VisitorType.PRIVATE
                }
                else -> {
                    consume(currentToken.type)
                    Statement.VisitorType.PUBLIC
                }
            }
            val state = this.get()
            return Statement.VisitorStatement(visitor,state)
        }



        fun eventStatement(): Statement.EventStatement {
            consume(TokenType.EVENT)
            val id = expr.identifier()
            val body = blockStatement()
            return Statement.EventStatement(id,body)
        }

        fun paramGetter(): ArrayList<Statement.VariableStatement> {
            val params = ArrayList<Statement.VariableStatement>()
            consume(TokenType.LEFT_PAREN)
            while (currentToken.type != TokenType.RIGHT_PAREN) {
                if (currentToken.type == TokenType.COMMA) {
                    consume(TokenType.COMMA)
                }
                when (currentToken.type) {
                    TokenType.CONST, TokenType.VAL -> params.add(varStatement(true, isParams = true))
                    TokenType.VAR -> params.add(varStatement(false, isParams = true))
                    else -> {
                        if (currentToken.type == TokenType.IDENTIFIER) {
                            params.add(varStatement(false, isParams = true))
                        }
                    }
                }
            }
            consume(TokenType.RIGHT_PAREN)
            return params
        }
        fun varStatement(isConst: Boolean = false, isParams: Boolean = false): Statement.VariableStatement {
            val start = Location(line, column)
            fun declaration(): Statement.VariableDeclaration {
                val id = expr.identifier()
                if (currentToken.type == TokenType.COLON) {
                    consume(TokenType.COLON)
                    if (isConst && !check(TokenType.EQUAL)) {
                        println(currentToken)
                        thrower.send("Constants must be initialized","NotInitializedError",file,currentToken)
                    } else {

                        val init = expr.typeGetter()

                        if (currentToken.type == TokenType.EQUAL) {
                            consume(TokenType.EQUAL)
                            if (init.values.first() != currentToken.type && init.values.first() != TokenType.OBJECT){
                                thrower.send("Non-specified type","SpecifiedTypeError",file,currentToken)
                            }
                            val inits = expr.primary()
                            return Statement.VariableDeclaration(id, inits)
                        }
                        return Statement.VariableDeclaration(id, init.keys.first())
                    }
                }
                if (isConst && currentToken.type != TokenType.EQUAL){
                    thrower.send("Constants must be initialized","NotInitializedError",file,currentToken)
                }else{
                    consume(TokenType.EQUAL)
                }

                val init = expr.get()
                return Statement.VariableDeclaration(id, init)
            }
            if (!isParams) {
                if (isConst) {
                    if (currentToken.type == TokenType.CONST) {
                        consume(TokenType.CONST)
                        if (currentToken.type == TokenType.VAL) {
                            consume(TokenType.VAL)
                        }
                    } else {
                        consume(TokenType.VAL)
                    }
                } else {
                    consume(TokenType.VAR)
                }
            } else {
                if (isConst) {
                    if (currentToken.type == TokenType.CONST) {
                        consume(TokenType.CONST)
                        if (currentToken.type == TokenType.VAL) {
                            consume(TokenType.VAL)
                        }
                    }

                } else {
                    if (currentToken.type == TokenType.VAR) {
                        consume(TokenType.VAR)
                    }
                }
            }
            val declaration = declaration()

            if (currentToken.type == TokenType.COMMA && isParams) {
                consume(TokenType.COMMA)
            } else if (currentToken.type == TokenType.RIGHT_PAREN) {
                val end = Location(line, column - 1)
                return Statement.VariableStatement(declaration, isConst, BigLocation(start,end))
            } else {
                spilt()
            }
            val end = Location(line, column - 1)
            return Statement.VariableStatement(declaration, isConst,BigLocation(start,end))
        }
        fun blockStatement(): Statement.BlockStatement {
            consume(TokenType.LEFT_BRACE)
            val statements = ArrayList<Statement>()
            while (currentToken.type != TokenType.RIGHT_BRACE) {
                get()?.let { statements.add(it) }
            }
            consume(TokenType.RIGHT_BRACE)
            return Statement.BlockStatement(statements)
        }
        fun functionStatement(): Statement.FunctionDeclaration {
            consume(TokenType.DEF)
            val id = expr.identifier()
            val params = paramGetter()
            val returnValue = if (currentToken.type == TokenType.COLON){
                consume(TokenType.COLON)
                expr.typeGetter().keys.first()
            }else{
                Expression.ObjectLiteral(null)
            }
            val body: Statement.BlockStatement = blockStatement()
            return Statement.FunctionDeclaration(id, params,returnValue,body)
        }
        fun returnStatement(): Statement.ReturnStatement {
            consume(TokenType.RETURN)
            val argument: Expression = expr.get()
            spilt()
            return Statement.ReturnStatement(argument)
        }
        fun whileStatement(): Statement.WhileStatement {
            consume(TokenType.WHILE)
            consume(TokenType.LEFT_PAREN)
            val rule = expr.get()
            consume(TokenType.RIGHT_PAREN)
            val body = blockStatement()
            return Statement.WhileStatement(rule, body)
        }
        fun doWhileStatement(): Statement.DoWhileStatement {
            consume(TokenType.DO)
            val body = blockStatement()
            consume(TokenType.WHILE)
            consume(TokenType.LEFT_PAREN)
            val rule = expr.get()
            consume(TokenType.RIGHT_PAREN)
            return Statement.DoWhileStatement(body, rule)
        }
        fun classStatement(): Statement.ClassDeclaration {
            consume(TokenType.CLASS)
            val id = expr.identifier()
            if (currentToken.type == TokenType.LEFT_PAREN) {
                val params = paramGetter()
                val body = blockStatement()
                return Statement.ClassDeclaration(id, params, body)
            }
            val body = blockStatement()

            return Statement.ClassDeclaration(id, null, body)
        }
        fun ifStatement(): Statement.IfStatement {
            consume(TokenType.IF)
            consume(TokenType.LEFT_PAREN)
            val rules = expr.get()

            consume(TokenType.RIGHT_PAREN)
            val consequent = blockStatement()
            if (currentToken.type == TokenType.ELSE) {
                consume(TokenType.ELSE)
                return if (currentToken.type == TokenType.IF) {
                    val alternate = ifStatement()
                    Statement.IfStatement(rules, consequent, alternate)
                } else {
                    val alternate = blockStatement()
                    Statement.IfStatement(rules, consequent, alternate)
                }
            }
            return Statement.IfStatement(rules, consequent, null)
        }
        fun tryStatement(): Statement.TryStatement {
            consume(TokenType.TRY)
            val body = blockStatement()
            if (currentToken.type == TokenType.FINALLY){
                consume(TokenType.FINALLY)
                val finally = blockStatement()
                return Statement.TryStatement(body,null,null,finally)
            }else{
                consume(TokenType.CATCH)
                consume(TokenType.LEFT_PAREN)
                val expect = expr.get()
                consume(TokenType.RIGHT_PAREN)
                val catch = blockStatement()
                if (currentToken.type == TokenType.FINALLY){
                    consume(TokenType.FINALLY)
                    val finally = blockStatement()
                    return Statement.TryStatement(body,expect,catch,finally)
                }
                return Statement.TryStatement(body, expect, catch)
            }
        }

        fun importStatement(): Statement.ImportStatement {
            consume(TokenType.INCLUDE)
            val file = expr.get()
            spilt()
            return Statement.ImportStatement(file)
        }

        fun switchStatement(): Statement.SwitchStatement {
            consume(TokenType.SWITCH)
            fun case(): Statement.CaseDeclaration {
                consume(TokenType.CASE)
                consume(TokenType.LEFT_PAREN)
                val rule = expr.get()
                consume(TokenType.RIGHT_PAREN)
                consume(TokenType.COLON)
                val body: Statement?
                if (currentToken.type == TokenType.LEFT_BRACE) body = blockStatement() else {
                    body = get()
                    advance()
                }
                return Statement.CaseDeclaration(rule, body)
            }
            consume(TokenType.LEFT_PAREN)
            val rule = expr.get()
            consume(TokenType.RIGHT_PAREN)
            consume(TokenType.LEFT_BRACE)
            val case = ArrayList<Statement.CaseDeclaration>()
            while (currentToken.type != TokenType.RIGHT_BRACE) {
                case.add(case())
            }
            consume(TokenType.RIGHT_BRACE)
            return Statement.SwitchStatement(rule, case)
        }

    }
    private inner class expression{
        val complex = listOf(
            TokenType.PLUS_EQUAL,
            TokenType.MINUS_EQUAL,
            TokenType.STAR_EQUAL,
            TokenType.SLASH_EQUAL,
            TokenType.MODULUS_EQUAL
        )
        val binary = listOf(
            TokenType.PLUS,
            TokenType.MINUS,
            TokenType.STAR,
            TokenType.MODULUS,
            TokenType.SLASH
        )
        val unary = listOf(
            TokenType.BANG,
            TokenType.MINUS,
            TokenType.Incrementing,
            TokenType.Subtraction
        )
        val logical = listOf(
            TokenType.LESS_EQUAL,
            TokenType.BANG_EQUAL,
            TokenType.GREATER,
            TokenType.GREATER_EQUAL,
            TokenType.LESS,
            TokenType.EQUAL_EQUAL
        )

        fun get() : Expression{
            return when(currentToken.type){
                TokenType.MINUS,TokenType.BANG,TokenType.Incrementing,TokenType.Subtraction -> unary()!!
                TokenType.IDENTIFIER -> {

                    if (check(TokenType.LEFT_PAREN)) {
                        val left = callee()
                        if (binary.contains(currentToken.type)){
                            return binary(left)
                        }
                        if (logical.contains(currentToken.type)){
                            return logical(left)
                        }
                        return left
                    }
                    if (check(TokenType.DOT) || check(TokenType.LEFT_SQUARE)) {
                        val left =  member()
                        if (binary.contains(currentToken.type)){
                            return binary(left)
                        }
                        if (logical.contains(currentToken.type)){
                            return logical(left)
                        }
                        return left
                    }
                    if (binary.contains(peek().type)) {
                        val left = primary()
                        return binary(left)
                    }else if (logical.contains(peek().type)){
                        val left = primary()
                        return logical(left)
                    }else if (check(TokenType.Incrementing) || check(TokenType.Subtraction))
                    {
                        return unary()!!
                    }
                    identifier()
                }
                else ->{
                    if (check(TokenType.DOT) || check(TokenType.LEFT_SQUARE)){
                        return member()
                    }
                    val left = primary()
                    if (binary.contains(currentToken.type)){
                        return binary(left)
                    }
                    if (logical.contains(currentToken.type)){
                        return logical(left)
                    }
                    return left
                }
            }
        }
        fun getLogical(){

        }
        fun isAssignmentOperator(): Token {
            if (currentToken.type == TokenType.EQUAL) return consume(TokenType.EQUAL)
            return when (currentToken.type) {
                TokenType.PLUS_EQUAL -> consume(complex[0])
                TokenType.MINUS_EQUAL -> consume(complex[1])
                TokenType.STAR_EQUAL -> consume(complex[2])
                TokenType.SLASH_EQUAL -> consume(complex[3])
                TokenType.MODULUS_EQUAL -> consume(complex[4])
                else -> consume(TokenType.EQUAL)
            }

        }

        fun logical(left: Expression,unAdvance : Boolean = false): Expression.LogicalExpression {
            val operator = currentToken.value
            advance()
            if (peek().type == TokenType.OR || peek().type == TokenType.AND) {
                val right = get()
                val ret = Expression.LogicalExpression(operator, left, right)
                return logical(ret)
            }
            val right = get()
            return Expression.LogicalExpression(operator, left, right)
        }

        fun unary(): Expression.UnaryExpression? {
            if (unary.contains(currentToken.type)){
                val operator = currentToken.value
                advance()
                val right = get()
                spilt()
                return Expression.UnaryExpression(operator, right,true)
            }else if (check(TokenType.Subtraction) || check(TokenType.Incrementing)){
                val operator = peek().value
                val left = get()
                advance()
                spilt()
                return Expression.UnaryExpression(operator, left,false)
            }
            return null
        }

        fun binary(left : Expression): Expression.BinaryExpression {
            val op = currentToken.value
            advance()
            val right = get()
            return Expression.BinaryExpression(op,left,right)
        }
        fun assignment(): Expression.AssignmentExpression {
            val left = get()
            val operator = isAssignmentOperator().value
            val right = get()
            spilt()
            return Expression.AssignmentExpression(left, operator, right)
        }
        fun callee(): Expression.CallExpression {
            val value: ArrayList<Expression> = arrayListOf()
            val id = identifier()
            consume(TokenType.LEFT_PAREN)
            while (currentToken.type != TokenType.RIGHT_PAREN) {
                if (currentToken.type == TokenType.COMMA) {
                    consume(currentToken.type)
                }
                value.add(get())
            }
            consume(TokenType.RIGHT_PAREN)
            return Expression.CallExpression(id, value)
        }
        fun member(expr: Expression? = null): Expression {
            var objectExpr = expr ?: primary()

            while (currentToken.type == TokenType.DOT || currentToken.type == TokenType.LEFT_SQUARE) {
                objectExpr = if (currentToken.type == TokenType.DOT) {
                    consume(TokenType.DOT)
                    val property = get()
                    Expression.MemberExpression(objectExpr, property, false)
                } else {
                    consume(TokenType.LEFT_SQUARE)
                    val property = get()
                    consume(TokenType.RIGHT_SQUARE)
                    Expression.MemberExpression(objectExpr, property, true)
                }
            }

            return objectExpr
        }
        fun primary(): Expression {
            return when (currentToken.type) {
                TokenType.IDENTIFIER -> {
                    return identifier()
                }
                TokenType.LEFT_PAREN -> {
                    consume(TokenType.LEFT_PAREN)
                    val ret = Expression.GroupExpression(get())
                    consume(TokenType.RIGHT_PAREN)
                    ret
                }
//                TokenType.DOT -> {
//                    currentToken = tokens[index - 1]
//                    return member()
//                }
                else -> Literal().literal
            }
        }
        fun typeGetter(): MutableMap<Expression, TokenType> {
            return when (currentToken.type) {
                TokenType.IDENTIFIER -> {
                    val identifier = Expression.Identifier(currentToken.value)
                    advance()
                    mutableMapOf(identifier to TokenType.IDENTIFIER)
                }

                TokenType.NUMBER -> {
                    val number = Expression.NumericLiteral(null)
                    advance()
                    mutableMapOf(number to TokenType.NUMBER)
                }

                TokenType.STRING -> {
                    val string = Expression.StringLiteral(null)
                    advance()
                    mutableMapOf(string to TokenType.STRING)
                }

                TokenType.BOOLEAN -> {
                    val boolean = Expression.BooleanLiteral(null)
                    advance()
                    mutableMapOf(boolean to TokenType.BOOLEAN)
                }

                TokenType.OBJECT -> {
                    val obj = Expression.ObjectLiteral(null)
                    advance()
                    mutableMapOf(obj to TokenType.OBJECT)
                }
                TokenType.VOID -> {
                    val void = Expression.VoidLiteral
                    advance()
                    mutableMapOf(void to TokenType.VOID)
                }

                else -> {
                    thrower.send("Unknown Type ['${currentToken.value}']","InvalidExpression",file,currentToken)
                    mutableMapOf(Expression.NullLiteral to TokenType.NULL)
                }
            }
        }
        fun identifier(): Expression.Identifier {
            return Expression.Identifier(consume(TokenType.IDENTIFIER).value)
        }
    }

    inner class Literal {
        val literal: Expression = when (currentToken.type) {
            TokenType.NUMBER -> NumericLiteral()
            TokenType.STRING -> StringLiteral()
            TokenType.FALSE, TokenType.TRUE -> BooleanLiteral()
            TokenType.NULL -> NullLiteral()
            TokenType.OBJECT -> ObjectLiteral()
            TokenType.VOID -> VoidLiteral()
            else -> {
                thrower.send("Unexpected literal production ['${currentToken.value}']","LiteralError",file,currentToken)
                Expression.NullLiteral
            }
        }



        val isLiteral: Boolean =
            currentToken.type == TokenType.NUMBER ||
                    currentToken.type == TokenType.STRING ||
                    currentToken.type == TokenType.FALSE ||
                    currentToken.type == TokenType.TRUE ||
                    currentToken.type == TokenType.OBJECT


        fun BooleanLiteral(): Expression.BooleanLiteral {
            return Expression.BooleanLiteral(consume(if (currentToken.type == TokenType.TRUE) TokenType.TRUE else TokenType.FALSE).value.toBoolean())
        }

        fun NumericLiteral(): Expression.NumericLiteral {
            return Expression.NumericLiteral(consume(TokenType.NUMBER).value.toDouble())
        }

        fun StringLiteral(): Expression.StringLiteral {

            return Expression.StringLiteral(consume(TokenType.STRING).value)
        }

        fun ObjectLiteral(): Expression.ObjectLiteral {
            return Expression.ObjectLiteral(consume(currentToken.type).value)
        }

        fun VoidLiteral(): Expression.VoidLiteral{
            consume(TokenType.VOID)
            return Expression.VoidLiteral
        }

        fun NullLiteral(): Expression.NullLiteral {
            consume(TokenType.NULL)
            return Expression.NullLiteral
        }
    }

    private fun peek(): Token {
        return if (!isEnd) tokens[index + 1] else currentToken
    }
    private fun past() : Token{
        return tokens[index - 1]
    }
    private fun check(type: TokenType) : Boolean{
        return peek().type == type
    }

    private fun advance(): Token {
        index += 1
        currentToken = if (index < tokens.size) tokens[index] else currentToken
        line = currentToken.location.line
        column = currentToken.location.column
        if (currentToken.type == TokenType.EOF || index >= tokens.size) isEnd = true
        return currentToken
    }

    private fun consume(tokenType: TokenType, error: String): Token {
        val token = currentToken
        if (currentToken.type != tokenType) {
            thrower.SyntaxError(error)
        }
        advance()
        return token
    }

    private fun spilt(){
        if(currentToken.type != TokenType.NEWLINE && currentToken.type != TokenType.EOF) consume(TokenType.SEMICOLON)
        else if (currentToken.type != TokenType.SEMICOLON && currentToken.type != TokenType.EOF && currentToken.type != TokenType.NEWLINE) consume(TokenType.SEMICOLON)
        else if (currentToken.type != TokenType.EOF && currentToken.type != TokenType.NEWLINE) consume(TokenType.SEMICOLON)
        else if (currentToken.type == TokenType.NEWLINE) consume(TokenType.NEWLINE)
        else if (currentToken.type == TokenType.SEMICOLON) consume(TokenType.SEMICOLON)
    }



    private fun consume(tokenType: TokenType): Token {
        val token = currentToken
        if (currentToken.type != tokenType) {
            thrower.send("Expect Token ['${tokenType.id}'] but is ['${token.type.id}']","TokenNotFound",file,token)

            //thrower.SyntaxError("Expect Token -> [\"${tokenType.id}\"] but not Found? Just Found [\"${currentToken.value}\"]")
        }
        advance()
        return token
    }

}