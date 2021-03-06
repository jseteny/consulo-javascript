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

package com.intellij.lang.javascript.psi.impl;

import org.jetbrains.annotations.NotNull;
import com.intellij.lang.ASTNode;
import com.intellij.lang.javascript.JSElementTypes;
import com.intellij.lang.javascript.psi.JSElementVisitor;
import com.intellij.lang.javascript.psi.JSUseNamespaceDirective;
import com.intellij.lang.javascript.psi.resolve.ResolveProcessor;
import com.intellij.lang.javascript.psi.stubs.JSUseNamespaceDirectiveStub;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.PsiScopeProcessor;

/**
 * @by Maxim.Mossienko
 */
public class JSUseNamespaceDirectiveImpl extends JSStubbedStatementImpl<JSUseNamespaceDirectiveStub> implements JSUseNamespaceDirective
{
	public JSUseNamespaceDirectiveImpl(final ASTNode node)
	{
		super(node);
	}

	public JSUseNamespaceDirectiveImpl(final JSUseNamespaceDirectiveStub stub)
	{
		super(stub, JSElementTypes.USE_NAMESPACE_DIRECTIVE);
	}

	@Override
	public void accept(@NotNull PsiElementVisitor visitor)
	{
		if(visitor instanceof JSElementVisitor)
		{
			((JSElementVisitor) visitor).visitJSUseNamespaceDirective(this);
		}
		else
		{
			visitor.visitElement(this);
		}
	}

	@Override
	public String getNamespaceToBeUsed()
	{
		final JSUseNamespaceDirectiveStub stub = getStub();
		if(stub != null)
		{
			return stub.getNamespaceToUse();
		}
		final ASTNode node = getNode().findChildByType(JSElementTypes.REFERENCE_EXPRESSION);
		return node != null ? node.getText() : null;
	}

	@Override
	public boolean processDeclarations(@NotNull PsiScopeProcessor processor, @NotNull ResolveState state, PsiElement lastParent,
			@NotNull PsiElement place)
	{
		if(processor instanceof ResolveProcessor && ((ResolveProcessor) processor).lookingForUseNamespaces())
		{
			return processor.execute(this, state);
		}
		return true;
	}
}
