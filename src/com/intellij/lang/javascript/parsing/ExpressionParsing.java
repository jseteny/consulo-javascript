/*
 * Copyright 2000-2005 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.lang.javascript.parsing;

import org.jetbrains.annotations.Nullable;
import com.intellij.javascript.documentation.JSDocumentationUtils;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.javascript.JSBundle;
import com.intellij.lang.javascript.JSElementTypes;
import com.intellij.lang.javascript.JSTokenTypes;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.psi.tree.IElementType;


/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jan 28, 2005
 * Time: 1:18:22 PM
 * To change this template use File | Settings | File Templates.
 */
@Deprecated
public class ExpressionParsing extends Parsing
{
	private static final Logger LOG = Logger.getInstance("#com.intellij.lang.javascript.parsing.ExpressionParsing");
	private static final Key<Boolean> WITHIN_ARRAY_LITERAL_EXPRESSION = Key.create("within.array.literal.expression");
	private static final Key<Boolean> WITHIN_OBJECT_LITERAL_EXPRESSION = Key.create("within.object.literal.expression");

	private ExpressionParsing()
	{
	}

	private static boolean parsePrimaryExpression(PsiBuilder builder)
	{
		final IElementType firstToken = builder.getTokenType();
		if(firstToken == JSTokenTypes.THIS_KEYWORD)
		{
			buildTokenElement(JSElementTypes.THIS_EXPRESSION, builder);
			return true;
		}
		else if(firstToken == JSTokenTypes.SUPER_KEYWORD)
		{
			buildTokenElement(JSElementTypes.SUPER_EXPRESSION, builder);
			return true;
		}
		else if(isIdentifierToken(firstToken) || firstToken == JSTokenTypes.ANY_IDENTIFIER)
		{
			buildTokenElement(JSElementTypes.REFERENCE_EXPRESSION, builder);
			return true;
		}
		else if(firstToken == JSTokenTypes.NUMERIC_LITERAL ||
				firstToken == JSTokenTypes.STRING_LITERAL ||
				firstToken == JSTokenTypes.REGEXP_LITERAL ||
				firstToken == JSTokenTypes.NULL_KEYWORD ||
				firstToken == JSTokenTypes.UNDEFINED_KEYWORD ||
				firstToken == JSTokenTypes.FALSE_KEYWORD ||
				firstToken == JSTokenTypes.TRUE_KEYWORD)
		{
			String errorMessage = validateLiteral(builder);
			buildTokenElement(JSElementTypes.LITERAL_EXPRESSION, builder);
			if(errorMessage != null)
			{
				builder.error(errorMessage);
			}
			return true;
		}
		else if(firstToken == JSTokenTypes.LPAR)
		{
			parseParenthesizedExpression(builder);
			return true;
		}
		else if(firstToken == JSTokenTypes.LBRACKET)
		{
			parseArrayLiteralExpression(builder);
			return true;
		}
		else if(firstToken == JSTokenTypes.LBRACE)
		{
			parseObjectLiteralExpression(builder);
			return true;
		}
		else if(firstToken == JSTokenTypes.LET_KEYWORD)
		{
			parseLetExpression(builder);
			return true;
		}
		else if(firstToken == JSTokenTypes.FUNCTION_KEYWORD)
		{
			FunctionParsing.parseFunctionExpression(builder);
			return true;
		}
		else if(JSTokenTypes.ACCESS_MODIFIERS.contains(firstToken))
		{
			PsiBuilder.Marker marker = builder.mark();
			builder.advanceLexer();
			if(JSTokenTypes.COLON_COLON == builder.getTokenType())
			{
				builder.advanceLexer();
				if(isIdentifierToken(builder.getTokenType()))
				{
					builder.advanceLexer();
				}
				marker.done(JSElementTypes.REFERENCE_EXPRESSION);
			}
			else
			{
				marker.drop();
			}
			return true;
		}
		else if(firstToken == JSTokenTypes.XML_START_TAG_START || firstToken == JSTokenTypes.XML_START_TAG_LIST)
		{
			parseTag(builder);
			return true;
		}
		else if(firstToken == JSTokenTypes.AT)
		{
			PsiBuilder.Marker marker = builder.mark();
			builder.advanceLexer();

			if(!builder.eof())
			{
				IElementType tokenType = builder.getTokenType();
				if(tokenType == JSTokenTypes.ANY_IDENTIFIER || isIdentifierToken(tokenType))
				{
					builder.advanceLexer();
				}
				else if(tokenType == JSTokenTypes.LBRACKET)
				{
					builder.advanceLexer();
					parseExpression(builder);
					checkMatches(builder, JSTokenTypes.RBRACKET, JSBundle.message("javascript.parser.message.expected.rbracket"));
				}
				else
				{
					builder.error(JSBundle.message("javascript.parser.message.expected.identifier"));
				}
			}

			marker.done(JSElementTypes.REFERENCE_EXPRESSION);

			return true;
		}
		else if(firstToken == JSTokenTypes.INT_KEYWORD || firstToken == JSTokenTypes.UINT_KEYWORD)
		{
			PsiBuilder.Marker marker = builder.mark();
			builder.advanceLexer();
			marker.done(JSElementTypes.REFERENCE_EXPRESSION); // ?

			return true;
		}
		else
		{
			return false;
		}
	}

	private static void parseLetExpression(final PsiBuilder builder)
	{
		final PsiBuilder.Marker marker = StatementParsing.parseLetExpressionStart(builder);
		parseExpression(builder);
		marker.done(JSElementTypes.LET_EXPRESSION);
	}

	private static void parseTag(final PsiBuilder builder)
	{
		final IElementType tokenType = builder.getTokenType();
		assert JSTokenTypes.XML_START_TAG_START == tokenType || JSTokenTypes.XML_START_TAG_LIST == tokenType;

		PsiBuilder.Marker marker = builder.mark();
		builder.advanceLexer();
		try
		{
			boolean endTagStarted = false;

			for(IElementType currentTokenType = builder.getTokenType(); currentTokenType != null; currentTokenType = builder.getTokenType())
			{
				if(!JSTokenTypes.XML_TOKENS.contains(currentTokenType))
				{
					PsiBuilder.Marker errorMarker = builder.mark();
					builder.advanceLexer();
					errorMarker.error(JSBundle.message("javascript.parser.message.expected.xml.element"));
					continue;
				}

				if(currentTokenType == JSTokenTypes.XML_START_TAG_START)
				{
					parseTag(builder);
					continue;
				}
				else if(currentTokenType == JSTokenTypes.XML_EMPTY_TAG_END ||
						currentTokenType == JSTokenTypes.XML_END_TAG_LIST ||
						(currentTokenType == JSTokenTypes.XML_TAG_END && endTagStarted))
				{
					builder.advanceLexer();
					return;
				}
				else if(currentTokenType == JSTokenTypes.XML_END_TAG_START)
				{
					endTagStarted = true;
				}

				builder.advanceLexer();
			}
		}
		finally
		{
			marker.done(JSElementTypes.XML_LITERAL_EXPRESSION);
		}
	}

	@Nullable
	private static String validateLiteral(final PsiBuilder builder)
	{
		final IElementType ttype = builder.getTokenType();
		if(ttype == JSTokenTypes.STRING_LITERAL)
		{
			final String ttext = builder.getTokenText();
			assert ttext != null;

			if(lastSymbolEscaped(ttext) ||
					ttext.startsWith("\"") && (!ttext.endsWith("\"") || ttext.length() == 1) ||
					ttext.startsWith("\'") && (!ttext.endsWith("\'") || ttext.length() == 1))
			{
				return JSBundle.message("javascript.parser.message.unclosed.string.literal");
			}
		}

		return null;
	}

	private static boolean lastSymbolEscaped(String text)
	{
		boolean escapes = false;
		boolean escaped = true;
		for(int i = 0; i < text.length(); i++)
		{
			char c = text.charAt(i);
			if(escapes)
			{
				escapes = false;
				escaped = true;
				continue;
			}
			if(c == '\\')
			{
				escapes = true;
			}
			escaped = false;
		}
		return escapes || escaped;
	}

	private static void parseObjectLiteralExpression(final PsiBuilder builder)
	{
		LOG.assertTrue(builder.getTokenType() == JSTokenTypes.LBRACE);
		final PsiBuilder.Marker expr = builder.mark();
		builder.advanceLexer();

		IElementType elementType = builder.getTokenType();

		while(elementType != JSTokenTypes.RBRACE && elementType != null)
		{
			parseProperty(builder);

			elementType = builder.getTokenType();
			if(elementType == JSTokenTypes.RBRACE)
			{
				break;
			}
			else if(elementType == JSTokenTypes.COMMA)
			{
				builder.advanceLexer();
			}
			else
			{
				builder.error(JSBundle.message("javascript.parser.message.expected.comma"));
			}

			elementType = builder.getTokenType();
			if(elementType == JSTokenTypes.RBRACE)
			{
				builder.error(JSBundle.message("javascript.parser.property.expected"));
			}
			else if(isNotPropertyStart(elementType))
			{
				break;
			}
		}

		checkMatches(builder, JSTokenTypes.RBRACE, JSBundle.message("javascript.parser.message.expected.rbrace"));
		expr.done(JSElementTypes.OBJECT_LITERAL_EXPRESSION);
	}

	private static boolean isNotPropertyStart(final IElementType elementType)
	{
		return !JSTokenTypes.IDENTIFIER_TOKENS_SET.contains(elementType) && elementType != JSTokenTypes.STRING_LITERAL && elementType != JSTokenTypes
				.NUMERIC_LITERAL;
	}

	private static void parseProperty(final PsiBuilder builder)
	{
		final IElementType nameToken = builder.getTokenType();
		final PsiBuilder.Marker property = builder.mark();

		String propName = JSTokenTypes.IDENTIFIER_TOKENS_SET.contains(nameToken) ? builder.getTokenText() : null;
		//noinspection HardCodedStringLiteral
		if("set".equals(propName) || "get".equals(propName))
		{
			builder.advanceLexer();
			if(builder.getTokenType() == JSTokenTypes.COLON)
			{
				builder.advanceLexer();
				if(!parseAssignmentExpression(builder))
				{
					builder.error(JSBundle.message("javascript.parser.message.expected.expression"));
				}
			}
			else
			{
				FunctionParsing.parseFunctionDeclaration(builder);
			}
		}
		else
		{
			if(isNotPropertyStart(nameToken))
			{
				builder.error(JSBundle.message("javascript.parser.message.expected.identifier.string.literal.or.numeric.literal"));
			}
			builder.advanceLexer();
			checkMatches(builder, JSTokenTypes.COLON, JSBundle.message("javascript.parser.message.expected.colon"));

			builder.putUserData(WITHIN_OBJECT_LITERAL_EXPRESSION, Boolean.TRUE);
			if(!parseAssignmentExpression(builder))
			{
				builder.error(JSBundle.message("javascript.parser.message.expected.expression"));
			}
			builder.putUserData(WITHIN_OBJECT_LITERAL_EXPRESSION, null);
		}

		property.done(JSElementTypes.PROPERTY);
	}

	private static void parseArrayLiteralExpression(final PsiBuilder builder)
	{
		LOG.assertTrue(builder.getTokenType() == JSTokenTypes.LBRACKET);
		final PsiBuilder.Marker expr = builder.mark();
		builder.advanceLexer();
		boolean commaExpected = false;

		Boolean save = null;

		try
		{
			save = builder.getUserData(WITHIN_ARRAY_LITERAL_EXPRESSION);
			builder.putUserData(WITHIN_ARRAY_LITERAL_EXPRESSION, Boolean.TRUE);

			while(builder.getTokenType() != JSTokenTypes.RBRACKET)
			{
				if(commaExpected)
				{
					final boolean b = checkMatches(builder, JSTokenTypes.COMMA, JSBundle.message("javascript.parser.message.expected.comma"));
					if(!b)
					{
						break;
					}
				}

				while(builder.getTokenType() == JSTokenTypes.COMMA)
				{
					builder.advanceLexer();
				}

				commaExpected = false;
				if(builder.getTokenType() != JSTokenTypes.RBRACKET)
				{
					if(!parseAssignmentExpression(builder))
					{
						builder.error(JSBundle.message("javascript.parser.message.expected.expression"));
						break;
					}
					else
					{
						commaExpected = true;
					}
				}
			}
			checkMatches(builder, JSTokenTypes.RBRACKET, JSBundle.message("javascript.parser.message.expected.rbracket"));
			expr.done(JSElementTypes.ARRAY_LITERAL_EXPRESSION);
		}
		finally
		{
			builder.putUserData(WITHIN_ARRAY_LITERAL_EXPRESSION, save);
		}
	}

	private static void parseParenthesizedExpression(final PsiBuilder builder)
	{
		LOG.assertTrue(builder.getTokenType() == JSTokenTypes.LPAR);
		final PsiBuilder.Marker expr = builder.mark();
		builder.advanceLexer();
		parseExpression(builder);
		checkMatches(builder, JSTokenTypes.RPAR, JSBundle.message("javascript.parser.message.expected.rparen"));
		expr.done(JSElementTypes.PARENTHESIZED_EXPRESSION);
	}

	private static boolean parseMemberExpression(PsiBuilder builder, boolean allowCallSyntax)
	{

		PsiBuilder.Marker expr = builder.mark();
		boolean isNew;

		final IElementType type = builder.getTokenType();

		if(type == JSTokenTypes.NEW_KEYWORD)
		{
			isNew = true;
			final boolean isfunction = parseNewExpression(builder);

			if(isfunction)
			{
				expr.done(JSElementTypes.NEW_EXPRESSION);
				if(builder.getTokenType() != JSTokenTypes.LPAR)
				{
					return true;
				}
				expr = expr.precede();
				isNew = false;
			}
		}
		else
		{
			isNew = false;
			if(!parsePrimaryExpression(builder))
			{
				expr.drop();
				return false;
			}
		}

		boolean currentlyAllowCallSyntax = allowCallSyntax && (type != JSTokenTypes.LBRACE && type != JSTokenTypes.LBRACKET);

		while(true)
		{
			IElementType tokenType = builder.getTokenType();
			if(tokenType == JSTokenTypes.DOT || (tokenType == JSTokenTypes.COLON_COLON || tokenType == JSTokenTypes.DOT_DOT))
			{
				currentlyAllowCallSyntax = allowCallSyntax;
				builder.advanceLexer();

				if(builder.getTokenType() == JSTokenTypes.AT)
				{

					builder.advanceLexer();
				}

				tokenType = builder.getTokenType();

				if(tokenType == JSTokenTypes.LT && isECMAL4(builder))
				{
					parseGenericSignature(builder);
					expr.done(JSElementTypes.REFERENCE_EXPRESSION);
					expr = expr.precede();
					continue;
				}

				if(tokenType == JSTokenTypes.LBRACKET || tokenType == JSTokenTypes.LPAR)
				{
					continue;
				}

				if(tokenType == JSTokenTypes.ANY_IDENTIFIER || isIdentifierToken(tokenType))
				{
					builder.advanceLexer();
				}
				else
				{
					builder.error(JSBundle.message("javascript.parser.message.expected.name"));
				}

				expr.done(JSElementTypes.REFERENCE_EXPRESSION);
				expr = expr.precede();
			}
			else if(tokenType == JSTokenTypes.LBRACKET)
			{
				builder.advanceLexer();
				parseExpression(builder);
				checkMatches(builder, JSTokenTypes.RBRACKET, JSBundle.message("javascript.parser.message.expected.rbracket"));
				expr.done(JSElementTypes.INDEXED_PROPERTY_ACCESS_EXPRESSION);
				expr = expr.precede();
			}
			else if(currentlyAllowCallSyntax && tokenType == JSTokenTypes.LPAR)
			{
				parseArgumentList(builder);
				expr.done(isNew ? JSElementTypes.NEW_EXPRESSION : JSElementTypes.CALL_EXPRESSION);
				expr = expr.precede();
				isNew = false;
			}
			else
			{
				if(isNew)
				{
					expr.done(JSElementTypes.NEW_EXPRESSION);
				}
				else
				{
					expr.drop();
				}
				break;
			}
		}

		return true;
	}

	public static boolean parseQualifiedTypeName(PsiBuilder builder)
	{
		return parseQualifiedTypeName(builder, false);
	}

	public static boolean parseQualifiedTypeName(PsiBuilder builder, boolean allowStar)
	{
		if(!JSTokenTypes.IDENTIFIER_TOKENS_SET.contains(builder.getTokenType()))
		{
			return false;
		}
		PsiBuilder.Marker expr = builder.mark();
		buildTokenElement(JSElementTypes.REFERENCE_EXPRESSION, builder);
		final boolean allowGenerics = isECMAL4(builder);
		boolean hasGenerics = false;

		while(builder.getTokenType() == JSTokenTypes.DOT)
		{
			boolean stop = false;
			builder.advanceLexer();

			final IElementType tokenType = builder.getTokenType();
			if(tokenType == JSTokenTypes.ANY_IDENTIFIER && allowStar)
			{
				builder.advanceLexer();
				stop = true;
			}
			else if(tokenType == JSTokenTypes.LT && allowGenerics)
			{
				parseGenericSignature(builder);
				stop = true;
				hasGenerics = true;
			}
			else if(tokenType == JSTokenTypes.DEFAULT_KEYWORD || (tokenType != JSTokenTypes.IDENTIFIER && JSTokenTypes.IDENTIFIER_TOKENS_SET.contains
					(tokenType)))
			{
				builder.advanceLexer(); // TODO: allow any keyword
			}
			else
			{
				checkMatches(builder, JSTokenTypes.IDENTIFIER, JSBundle.message("javascript.parser.message.expected.name"));
			}
			expr.done(JSElementTypes.REFERENCE_EXPRESSION);
			expr = expr.precede();

			if(stop)
			{
				break;
			}
		}

		if(!hasGenerics && allowGenerics && builder.getTokenType() == JSTokenTypes.LT)
		{
			parseGenericSignature(builder);
			expr.done(JSElementTypes.REFERENCE_EXPRESSION);
		}
		else
		{
			expr.drop();
		}
		return true;
	}

	private static void parseGenericSignature(final PsiBuilder builder)
	{
		assert builder.getTokenType() == JSTokenTypes.LT;
		PsiBuilder.Marker genericTypeSignature = builder.mark();
		builder.advanceLexer();
		parseType(builder);
		checkMatches(builder, JSTokenTypes.GT, JSBundle.message("javascript.parser.message.expected.lt"));
		genericTypeSignature.done(JSElementTypes.GENERIC_SIGNATURE);
	}

	private static boolean parseNewExpression(PsiBuilder builder)
	{
		LOG.assertTrue(builder.getTokenType() == JSTokenTypes.NEW_KEYWORD);
		builder.advanceLexer();

		if(builder.getTokenType() == JSTokenTypes.FUNCTION_KEYWORD)
		{
			FunctionParsing.parseFunctionExpression(builder);
			return true;
		}

		if(!parseMemberExpression(builder, false))
		{
			builder.error(JSBundle.message("javascript.parser.message.expected.expression"));
		}
		return false;
	}

	private static void parseArgumentList(final PsiBuilder builder)
	{
		LOG.assertTrue(builder.getTokenType() == JSTokenTypes.LPAR);
		final PsiBuilder.Marker arglist = builder.mark();
		builder.advanceLexer();
		boolean first = true;
		while(builder.getTokenType() != JSTokenTypes.RPAR)
		{
			if(first)
			{
				first = false;
			}
			else
			{
				if(builder.getTokenType() == JSTokenTypes.COMMA)
				{
					builder.advanceLexer();
				}
				else
				{
					builder.error(JSBundle.message("javascript.parser.message.expected.comma.or.rparen"));
					break;
				}
			}
			if(!parseAssignmentExpression(builder))
			{
				builder.error(JSBundle.message("javascript.parser.message.expected.expression"));
			}
		}

		checkMatches(builder, JSTokenTypes.RPAR, JSBundle.message("javascript.parser.message.expected.rparen"));
		arglist.done(JSElementTypes.ARGUMENT_LIST);
	}

	public static void parseExpression(PsiBuilder builder)
	{
		if(!parseExpressionOptional(builder))
		{
			builder.error(JSBundle.message("javascript.parser.message.expected.expression"));
		}
	}

	public static boolean parseAssignmentExpressionNoIn(final PsiBuilder builder)
	{
		return parseAssignmentExpression(builder, false);
	}

	public static boolean parseAssignmentExpression(final PsiBuilder builder)
	{
		return parseAssignmentExpression(builder, true);
	}

	private static boolean parseAssignmentExpression(final PsiBuilder builder, boolean allowIn)
	{
		final PsiBuilder.Marker expr = builder.mark();

		if(JSTokenTypes.ASSIGNMENT_OPERATIONS.contains(builder.getTokenType()) && builder.getUserData(WITHIN_OBJECT_LITERAL_EXPRESSION) == null)
		{
			builder.error(JSBundle.message("javascript.parser.message.expected.expression"));
			builder.advanceLexer();
			if(!parseAssignmentExpression(builder, allowIn))
			{
				builder.error(JSBundle.message("javascript.parser.message.expected.expression"));
			}
			expr.done(JSElementTypes.ASSIGNMENT_EXPRESSION);
			return true;
		}

		final PsiBuilder.Marker definitionExpr = builder.mark();
		if(!parseConditionalExpression(builder, allowIn))
		{
			definitionExpr.drop();
			expr.drop();
			return false;
		}

		if(JSTokenTypes.ASSIGNMENT_OPERATIONS.contains(builder.getTokenType()))
		{
			definitionExpr.done(JSElementTypes.DEFINITION_EXPRESSION);
			builder.advanceLexer();
			if(!parseAssignmentExpression(builder, allowIn))
			{
				builder.error(JSBundle.message("javascript.parser.message.expected.expression"));
			}
			expr.done(JSElementTypes.ASSIGNMENT_EXPRESSION);
		}
		else
		{
			definitionExpr.drop();
			expr.drop();
		}
		return true;
	}

	private static boolean parseConditionalExpression(final PsiBuilder builder, final boolean allowIn)
	{
		final PsiBuilder.Marker expr = builder.mark();
		if(!parseORExpression(builder, allowIn))
		{
			expr.drop();
			return false;
		}

		final IElementType nextTokenType = builder.getTokenType();

		if(nextTokenType == JSTokenTypes.QUEST)
		{
			builder.advanceLexer();
			if(!parseAssignmentExpression(builder, allowIn))
			{
				builder.error(JSBundle.message("javascript.parser.message.expected.expression"));
			}
			checkMatches(builder, JSTokenTypes.COLON, JSBundle.message("javascript.parser.message.expected.colon"));
			if(!parseAssignmentExpression(builder, allowIn))
			{
				builder.error(JSBundle.message("javascript.parser.message.expected.expression"));
			}
			expr.done(JSElementTypes.CONDITIONAL_EXPRESSION);
		}
		else if(nextTokenType == JSTokenTypes.FOR_KEYWORD && builder.getUserData(WITHIN_ARRAY_LITERAL_EXPRESSION) != null)
		{
			StatementParsing.parseForLoopHeader(builder);      // TODO: make it more clear
			expr.done(JSElementTypes.CONDITIONAL_EXPRESSION);
		}
		else
		{
			expr.drop();
		}
		return true;
	}

	private static boolean parseORExpression(final PsiBuilder builder, final boolean allowIn)
	{
		PsiBuilder.Marker expr = builder.mark();
		if(!parseANDExpression(builder, allowIn))
		{
			expr.drop();
			return false;
		}

		while(builder.getTokenType() == JSTokenTypes.OROR)
		{
			builder.advanceLexer();
			if(!parseANDExpression(builder, allowIn))
			{
				builder.error(JSBundle.message("javascript.parser.message.expected.expression"));
			}
			expr.done(JSElementTypes.BINARY_EXPRESSION);
			expr = expr.precede();
		}

		expr.drop();
		return true;
	}

	private static boolean parseANDExpression(final PsiBuilder builder, final boolean allowIn)
	{
		PsiBuilder.Marker expr = builder.mark();
		if(!parseBitwiseORExpression(builder, allowIn))
		{
			expr.drop();
			return false;
		}

		while(builder.getTokenType() == JSTokenTypes.ANDAND)
		{
			builder.advanceLexer();
			if(!parseBitwiseORExpression(builder, allowIn))
			{
				builder.error(JSBundle.message("javascript.parser.message.expected.expression"));
			}
			expr.done(JSElementTypes.BINARY_EXPRESSION);
			expr = expr.precede();
		}

		expr.drop();
		return true;
	}

	private static boolean parseBitwiseORExpression(final PsiBuilder builder, final boolean allowIn)
	{
		PsiBuilder.Marker expr = builder.mark();
		if(!parseBitwiseXORExpression(builder, allowIn))
		{
			expr.drop();
			return false;
		}

		while(builder.getTokenType() == JSTokenTypes.OR)
		{
			builder.advanceLexer();
			if(!parseBitwiseXORExpression(builder, allowIn))
			{
				builder.error(JSBundle.message("javascript.parser.message.expected.expression"));
			}
			expr.done(JSElementTypes.BINARY_EXPRESSION);
			expr = expr.precede();
		}

		expr.drop();
		return true;
	}

	private static boolean parseBitwiseXORExpression(final PsiBuilder builder, final boolean allowIn)
	{
		PsiBuilder.Marker expr = builder.mark();
		if(!parseBitwiseANDExpression(builder, allowIn))
		{
			expr.drop();
			return false;
		}

		while(builder.getTokenType() == JSTokenTypes.XOR)
		{
			builder.advanceLexer();
			if(!parseBitwiseANDExpression(builder, allowIn))
			{
				builder.error(JSBundle.message("javascript.parser.message.expected.expression"));
			}
			expr.done(JSElementTypes.BINARY_EXPRESSION);
			expr = expr.precede();
		}

		expr.drop();
		return true;
	}

	private static boolean parseBitwiseANDExpression(final PsiBuilder builder, final boolean allowIn)
	{
		PsiBuilder.Marker expr = builder.mark();
		if(!parseEqualityExpression(builder, allowIn))
		{
			expr.drop();
			return false;
		}

		while(builder.getTokenType() == JSTokenTypes.AND)
		{
			builder.advanceLexer();
			if(!parseEqualityExpression(builder, allowIn))
			{
				builder.error(JSBundle.message("javascript.parser.message.expected.expression"));
			}
			expr.done(JSElementTypes.BINARY_EXPRESSION);
			expr = expr.precede();
		}

		expr.drop();
		return true;
	}

	private static boolean parseEqualityExpression(final PsiBuilder builder, final boolean allowIn)
	{
		PsiBuilder.Marker expr = builder.mark();
		if(!parseRelationalExpression(builder, allowIn))
		{
			expr.drop();
			return false;
		}

		while(JSTokenTypes.EQUALITY_OPERATIONS.contains(builder.getTokenType()))
		{
			builder.advanceLexer();
			if(!parseRelationalExpression(builder, allowIn))
			{
				builder.error(JSBundle.message("javascript.parser.message.expected.expression"));
			}
			expr.done(JSElementTypes.BINARY_EXPRESSION);
			expr = expr.precede();
		}

		expr.drop();
		return true;
	}

	private static boolean parseRelationalExpression(final PsiBuilder builder, final boolean allowIn)
	{
		PsiBuilder.Marker expr = builder.mark();
		if(!parseShiftExpression(builder))
		{
			expr.drop();
			return false;
		}
		while(JSTokenTypes.RELATIONAL_OPERATIONS.contains(builder.getTokenType()) && (allowIn || builder.getTokenType() != JSTokenTypes.IN_KEYWORD))
		{
			builder.advanceLexer();
			if(!parseShiftExpression(builder))
			{
				builder.error(JSBundle.message("javascript.parser.message.expected.expression"));
			}
			expr.done(JSElementTypes.BINARY_EXPRESSION);
			expr = expr.precede();
		}

		expr.drop();
		return true;
	}

	private static boolean parseShiftExpression(final PsiBuilder builder)
	{
		PsiBuilder.Marker expr = builder.mark();
		if(!parseAdditiveExpression(builder))
		{
			expr.drop();
			return false;
		}
		while(JSTokenTypes.SHIFT_OPERATIONS.contains(builder.getTokenType()))
		{
			builder.advanceLexer();
			if(!parseAdditiveExpression(builder))
			{
				builder.error(JSBundle.message("javascript.parser.message.expected.expression"));
			}
			expr.done(JSElementTypes.BINARY_EXPRESSION);
			expr = expr.precede();
		}

		expr.drop();
		return true;
	}

	private static boolean parseAdditiveExpression(final PsiBuilder builder)
	{
		PsiBuilder.Marker expr = builder.mark();
		if(!parseMultiplicativeExpression(builder))
		{
			expr.drop();
			return false;
		}
		while(JSTokenTypes.ADDITIVE_OPERATIONS.contains(builder.getTokenType()))
		{
			builder.advanceLexer();
			if(!parseMultiplicativeExpression(builder))
			{
				builder.error(JSBundle.message("javascript.parser.message.expected.expression"));
			}
			expr.done(JSElementTypes.BINARY_EXPRESSION);
			expr = expr.precede();
		}

		expr.drop();
		return true;
	}

	private static boolean parseIsAsExpression(final PsiBuilder builder)
	{
		PsiBuilder.Marker expr = builder.mark();
		if(!parseUnaryExpression(builder))
		{
			expr.drop();
			return false;
		}

		while(builder.getTokenType() == JSTokenTypes.AS_KEYWORD || builder.getTokenType() == JSTokenTypes.IS_KEYWORD)
		{
			builder.advanceLexer();
			if(!parseUnaryExpression(builder))
			{
				builder.error(JSBundle.message("javascript.parser.message.expected.expression"));
			}
			expr.done(JSElementTypes.BINARY_EXPRESSION);
			expr = expr.precede();
		}

		expr.drop();
		return true;
	}

	private static boolean parseMultiplicativeExpression(final PsiBuilder builder)
	{
		PsiBuilder.Marker expr = builder.mark();
		if(!parseIsAsExpression(builder))
		{
			expr.drop();
			return false;
		}

		while(JSTokenTypes.MULTIPLICATIVE_OPERATIONS.contains(builder.getTokenType()))
		{
			builder.advanceLexer();
			if(!parseUnaryExpression(builder))
			{
				builder.error(JSBundle.message("javascript.parser.message.expected.expression"));
			}
			expr.done(JSElementTypes.BINARY_EXPRESSION);
			expr = expr.precede();
		}

		expr.drop();
		return true;
	}

	private static boolean parseUnaryExpression(final PsiBuilder builder)
	{
		final IElementType tokenType = builder.getTokenType();
		if(JSTokenTypes.UNARY_OPERATIONS.contains(tokenType))
		{
			final PsiBuilder.Marker expr = builder.mark();
			builder.advanceLexer();
			if(!parseUnaryExpression(builder))
			{
				builder.error(JSBundle.message("javascript.parser.message.expected.expression"));
			}
			expr.done(JSElementTypes.PREFIX_EXPRESSION);
			return true;
		}
		else
		{
			return parsePostfixExpression(builder);
		}
	}

	private static boolean parsePostfixExpression(PsiBuilder builder)
	{
		final PsiBuilder.Marker expr = builder.mark();
		if(!parseMemberExpression(builder, true))
		{
			expr.drop();
			return false;
		}

		final IElementType tokenType = builder.getTokenType();
		if(tokenType == JSTokenTypes.PLUSPLUS || tokenType == JSTokenTypes.MINUSMINUS)
		{
			builder.advanceLexer();
			expr.done(JSElementTypes.POSTFIX_EXPRESSION);
		}
		else
		{
			expr.drop();
		}
		return true;
	}

	public static boolean parseExpressionOptional(final PsiBuilder builder)
	{
		return parseExpressionOptional(builder, true);
	}

	public static boolean parseExpressionOptionalNoIn(final PsiBuilder builder)
	{
		return parseExpressionOptional(builder, false);
	}

	public static boolean parseExpressionOptional(final PsiBuilder builder, final boolean allowIn)
	{
		PsiBuilder.Marker expr = builder.mark();
		if(!parseAssignmentExpression(builder, allowIn))
		{
			expr.drop();
			return false;
		}

		if(builder.getTokenType() == JSTokenTypes.IN_KEYWORD)
		{
			expr.done(JSElementTypes.DEFINITION_EXPRESSION);
			return true;
		}

		while(builder.getTokenType() == JSTokenTypes.COMMA)
		{
			builder.advanceLexer();
			if(!parseAssignmentExpression(builder, allowIn))
			{
				builder.error(JSBundle.message("javascript.parser.message.expected.expression"));
			}

			expr.done(JSElementTypes.COMMA_EXPRESSION);
			expr = expr.precede();
		}

		expr.drop();

		return true;
	}

	private static void buildTokenElement(IElementType type, PsiBuilder builder)
	{
		final PsiBuilder.Marker marker = builder.mark();
		builder.advanceLexer();
		marker.done(type);
	}

	static boolean tryParseType(final PsiBuilder builder)
	{
		if(builder.getTokenType() == JSTokenTypes.COLON && isECMAL4(builder))
		{
			builder.advanceLexer();

			return parseType(builder);
		}

		return false;
	}

	public static boolean parseType(final PsiBuilder builder)
	{
		final IElementType tokenType = builder.getTokenType();
		if(JSDocumentationUtils.ourPrimitiveTypeFilter.contains(tokenType))
		{
			builder.advanceLexer();
		}
		else if(!parseQualifiedTypeName(builder))
		{
			builder.error(JSBundle.message("javascript.parser.message.expected.typename"));
		}
		return true;
	}

	public static void parseSimpleExpression(final PsiBuilder builder)
	{
		if(!parseUnaryExpression(builder))
		{
			builder.error(JSBundle.message("javascript.parser.message.expected.expression"));
		}
	}

	public static void parseJSON(final PsiBuilder builder)
	{
		if(builder.getTokenType() == JSTokenTypes.LBRACKET)
		{
			parseArrayLiteralExpression(builder);
			if(builder.getTokenType() != null)
			{
				builder.error(JSBundle.message("javascript.parser.message.expected.eof"));
			}
		}
		else if(builder.getTokenType() == JSTokenTypes.LBRACE)
		{
			parseObjectLiteralExpression(builder);
			if(builder.getTokenType() != null)
			{
				builder.error(JSBundle.message("javascript.parser.message.expected.eof"));
			}
		}
		else
		{
			builder.error(JSBundle.message("javascript.parser.message.expected.lbrace.or.lbracket"));
		}

		while(builder.getTokenType() != null)
		{
			builder.advanceLexer();
		}
	}

	public static void parseScriptExpression(final PsiBuilder builder)
	{
		PsiBuilder.Marker root = builder.mark();
		checkMatches(builder, JSTokenTypes.LBRACE, JSBundle.message("javascript.parser.message.expected.lbrace"));
		parseExpression(builder);
		checkMatches(builder, JSTokenTypes.RBRACE, JSBundle.message("javascript.parser.message.expected.rbrace"));

		while(!builder.eof())
		{
			builder.advanceLexer();
		}

		root.done(JSElementTypes.EMBEDDED_EXPRESSION);
	}

}
