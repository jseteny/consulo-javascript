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

package com.intellij.javascript;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.template.Expression;
import com.intellij.codeInsight.template.ExpressionContext;
import com.intellij.codeInsight.template.Macro;
import com.intellij.codeInsight.template.Result;
import com.intellij.codeInsight.template.TextResult;
import com.intellij.lang.javascript.JSBundle;
import com.intellij.lang.javascript.psi.JSFunction;
import com.intellij.lang.javascript.psi.resolve.JSResolveUtil;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;

public class JSClassNameMacro extends Macro
{
	@Override
	@NonNls
	public String getName()
	{
		return "jsClassName";
	}

	@Override
	public String getPresentableName()
	{
		return JSBundle.message("js.classname.macro.description");
	}

	@Override
	@NonNls
	public String getDefaultValue()
	{
		return "";
	}

	@Override
	public Result calculateResult(@NotNull final Expression[] params, final ExpressionContext context)
	{
		final PsiElement elementAtCaret = findElementAtCaret(context);
		final JSResolveUtil.ContextResolver resolver = new JSResolveUtil.ContextResolver(elementAtCaret);

		String text = resolver.getQualifierAsString();
		if(text == null)
		{
			final JSFunction previousFunction = PsiTreeUtil.getPrevSiblingOfType(elementAtCaret, JSFunction.class);

			if(previousFunction != null)
			{
				text = previousFunction.getName();
			}
		}

		if(text != null)
		{
			return new TextResult(text);
		}

		return null;
	}

	public static PsiElement findElementAtCaret(final ExpressionContext context)
	{
		Project project = context.getProject();
		int templateStartOffset = context.getTemplateStartOffset();
		int offset = templateStartOffset > 0 ? context.getTemplateStartOffset() - 1 : context.getTemplateStartOffset();

		PsiDocumentManager.getInstance(project).commitAllDocuments();

		PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(context.getEditor().getDocument());
		return file.findElementAt(offset);
	}

	@Override
	public Result calculateQuickResult(@NotNull final Expression[] params, final ExpressionContext context)
	{
		return null;
	}

	@Override
	public LookupElement[] calculateLookupItems(@NotNull final Expression[] params, final ExpressionContext context)
	{
		return null;
	}
}
