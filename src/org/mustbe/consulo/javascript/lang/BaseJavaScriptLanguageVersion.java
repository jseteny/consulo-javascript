package org.mustbe.consulo.javascript.lang;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mustbe.consulo.javascript.lang.parsing.JavaScriptParser;
import com.intellij.lang.BaseLanguageVersion;
import com.intellij.lang.LanguageVersionWithParsing;
import com.intellij.lang.PsiParser;
import com.intellij.lang.javascript.JSTokenTypes;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.project.Project;
import com.intellij.psi.tree.TokenSet;

/**
 * @author VISTALL
 * @since 24.08.14
 */
public class BaseJavaScriptLanguageVersion extends BaseLanguageVersion<JavaScriptLanguage> implements LanguageVersionWithParsing<JavaScriptLanguage>
{
	public BaseJavaScriptLanguageVersion(String name)
	{
		super(name, JavaScriptLanguage.INSTANCE);
	}

	@NotNull
	@Override
	public Lexer createLexer(@Nullable Project project)
	{
		return null;
	}

	@NotNull
	@Override
	public PsiParser createParser(@Nullable Project project)
	{
		return new JavaScriptParser();
	}

	@NotNull
	@Override
	public TokenSet getWhitespaceTokens()
	{
		return JSTokenSets.WHITE_SPACES;
	}

	@NotNull
	@Override
	public TokenSet getCommentTokens()
	{
		return JSTokenTypes.COMMENTS;
	}

	@NotNull
	@Override
	public TokenSet getStringLiteralElements()
	{
		return JSTokenSets.STRING_LITERALS;
	}
}