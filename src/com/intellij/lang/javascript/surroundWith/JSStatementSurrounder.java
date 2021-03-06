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

package com.intellij.lang.javascript.surroundWith;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.intellij.lang.ASTNode;
import com.intellij.lang.javascript.JSLanguageDialect;
import com.intellij.lang.javascript.psi.impl.JSChangeUtil;
import com.intellij.lang.javascript.psi.util.JSUtils;
import com.intellij.lang.surroundWith.Surrounder;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.IncorrectOperationException;

/**
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 12.07.2005
 * Time: 16:50:58
 * To change this template use File | Settings | File Templates.
 */
public abstract class JSStatementSurrounder implements Surrounder
{
	@Override
	public boolean isApplicable(@NotNull PsiElement[] elements)
	{
		return true;
	}

	@Override
	@Nullable
	public TextRange surroundElements(@NotNull Project project, @NotNull Editor editor, @NotNull PsiElement[] elements) throws
			IncorrectOperationException
	{
		final JSLanguageDialect languageDialect = JSUtils.getDialect(elements[0].getContainingFile());
		ASTNode node = JSChangeUtil.createStatementFromText(project, getStatementTemplate(project, elements[0]), languageDialect);

		PsiElement container = elements[0].getParent();
		container.getNode().addChild(node, elements[0].getNode());
		final ASTNode insertBeforeNode = getInsertBeforeNode(node);

		for(int i = 0; i < elements.length; i++)
		{
			final ASTNode childNode = elements[i].getNode();
			final ASTNode childNodeCopy = childNode.copyElement();

			container.getNode().removeChild(childNode);
			insertBeforeNode.getTreeParent().addChild(childNodeCopy, insertBeforeNode);
		}

		final CodeStyleManager csManager = CodeStyleManager.getInstance(project);
		csManager.reformat(node.getPsi());

		return getSurroundSelectionRange(node);
	}

	protected abstract
	@NonNls
	String getStatementTemplate(final Project project, PsiElement context);

	protected abstract ASTNode getInsertBeforeNode(final ASTNode statementNode);

	protected abstract TextRange getSurroundSelectionRange(final ASTNode statementNode);
}
