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
import com.intellij.javascript.documentation.JSDocumentationUtils;
import com.intellij.lang.ASTNode;
import com.intellij.lang.javascript.JSElementTypes;
import com.intellij.lang.javascript.JSTokenTypes;
import com.intellij.lang.javascript.JavascriptLanguage;
import com.intellij.lang.javascript.psi.JSAttributeList;
import com.intellij.lang.javascript.psi.JSClass;
import com.intellij.lang.javascript.psi.JSElement;
import com.intellij.lang.javascript.psi.JSElementVisitor;
import com.intellij.lang.javascript.psi.JSFunction;
import com.intellij.lang.javascript.psi.JSParameter;
import com.intellij.lang.javascript.psi.JSParameterList;
import com.intellij.lang.javascript.psi.JSReferenceExpression;
import com.intellij.lang.javascript.psi.JSSourceElement;
import com.intellij.lang.javascript.psi.resolve.JSImportHandlingUtil;
import com.intellij.lang.javascript.psi.resolve.JSResolveUtil;
import com.intellij.lang.javascript.psi.stubs.JSFunctionStub;
import com.intellij.psi.PsiElement;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.IncorrectOperationException;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jan 30, 2005
 * Time: 8:25:27 PM
 */
abstract class JSFunctionBaseImpl<T extends JSFunctionStub, T2 extends JSFunction> extends JSStubElementImpl<T> implements JSFunction
{
	private boolean referencesArgumentsCalculated;
	private boolean referencesArguments;

	public JSFunctionBaseImpl(final ASTNode node)
	{
		super(node);
	}

	public JSFunctionBaseImpl(final T stub, IStubElementType type)
	{
		super(stub, type);
	}

	@Override
	public void subtreeChanged()
	{
		super.subtreeChanged();
		referencesArgumentsCalculated = false;
		referencesArguments = false;
	}

	@Override
	public JSParameterList getParameterList()
	{
		return getStubOrPsiChild(JSElementTypes.PARAMETER_LIST);
	}

	@Override
	public JSSourceElement[] getBody()
	{
		final ASTNode[] children = getNode().getChildren(JSElementTypes.SOURCE_ELEMENTS);
		if(children.length == 0)
		{
			return JSSourceElement.EMPTY_ARRAY;
		}
		JSSourceElement[] result = new JSSourceElement[children.length];
		for(int i = 0; i < children.length; i++)
		{
			result[i] = (JSSourceElement) children[i].getPsi();
		}
		return result;
	}

	@Override
	public String getReturnTypeString()
	{
		final T stub = getStub();
		if(stub != null)
		{
			return stub.getReturnTypeString();
		}
		return JSPsiImplUtils.getType(this);
	}

	@Override
	public PsiElement getReturnTypeElement()
	{
		ASTNode node = JSPsiImplUtils.getTypeExpressionFromDeclaration(this);
		return node != null ? node.getPsi() : null;
	}

	@Override
	public PsiElement setName(@NotNull String name) throws IncorrectOperationException
	{
		final boolean isConstructor = isConstructor();
		final ASTNode newNameElement = createNameIdentifier(name);
		final ASTNode nameIdentifier = findNameIdentifier();
		nameIdentifier.getTreeParent().replaceChild(nameIdentifier, newNameElement);

		if(isConstructor)
		{
			((JSClass) getParent()).setName(name);
		}
		return this;
	}

	protected ASTNode createNameIdentifier(final String name)
	{
		return JSChangeUtil.createExpressionFromText(getProject(), name);
	}

	@Override
	public String getName()
	{
		final JSFunctionStub stub = getStub();
		if(stub != null)
		{
			return stub.getName();
		}
		final ASTNode name = findNameIdentifier();

		if(name != null)
		{
			final PsiElement psi = name.getPsi();
			if(psi instanceof JSReferenceExpression)
			{
				return ((JSReferenceExpression) psi).getReferencedName();
			}
			else
			{
				return name.getText();
			}
		}
		return null;
	}

	@Override
	public ASTNode findNameIdentifier()
	{
		final ASTNode myNode = getNode();
		ASTNode astNode = myNode.findChildByType(JSTokenTypes.FUNCTION_KEYWORD);

		if(astNode != null)
		{
			astNode = advance(astNode);
		}
		else
		{
			astNode = myNode.findChildByType(JSElementTypes.REFERENCE_EXPRESSION);
		}

		IElementType type = astNode != null ? astNode.getElementType() : null;
		ASTNode prevAstNode = null;

		if(type == JSTokenTypes.GET_KEYWORD || type == JSTokenTypes.SET_KEYWORD)
		{
			prevAstNode = astNode;
			astNode = advance(astNode);
			type = astNode.getElementType();
		}

		if(JSVariableBaseImpl.IDENTIFIER_TOKENS_SET.contains(type))
		{
			return astNode;
		}
		if(prevAstNode != null)
		{
			return prevAstNode;
		}

		return null;
	}

	private static ASTNode advance(ASTNode astNode)
	{
		astNode = astNode != null ? astNode.getTreeNext() : null;

		if(astNode != null && astNode.getElementType() == JSTokenTypes.WHITE_SPACE)
		{
			astNode = astNode.getTreeNext();
		}
		return astNode;
	}

	@Override
	public int getTextOffset()
	{
		final ASTNode name = findNameIdentifier();
		return name != null ? name.getStartOffset() : super.getTextOffset();
	}

	@Override
	public boolean processDeclarations(@NotNull PsiScopeProcessor processor, @NotNull ResolveState state, PsiElement lastParent,
			@NotNull PsiElement place)
	{
		if(lastParent != null && lastParent.getParent() == this)
		{
			if(place instanceof JSReferenceExpression)
			{
				boolean b = JSImportHandlingUtil.tryResolveImports(processor, this, place);
				if(!b || JSResolveUtil.isExprInStrictTypeContext((JSReferenceExpression) place))
				{
					return b;
				}
			}

			final JSParameter[] params = getParameterList().getParameters();
			for(JSParameter param : params)
			{
				if(!processor.execute(param, state))
				{
					return false;
				}
			}

			boolean b = JSResolveUtil.processDeclarationsInScope(this, processor, state, lastParent, place);
			if(b)
			{
				processor.handleEvent(PsiScopeProcessor.Event.SET_DECLARATION_HOLDER, this);
			}
			return b;
		}

		return processor.execute(this, state);
	}

	@Override
	public PsiElement addBefore(@NotNull final PsiElement element, final PsiElement anchor) throws IncorrectOperationException
	{
		if(anchor == getFirstChild() && element instanceof JSAttributeList && anchor.getNode().getElementType() == JSTokenTypes.FUNCTION_KEYWORD)
		{
			return JSChangeUtil.doDoAddBefore(this, element, anchor);
		}
		return super.addBefore(element, anchor);
	}

	@Override
	public FunctionKind getKind()
	{
		if(isGetProperty())
		{
			return FunctionKind.GETTER;
		}
		if(isSetProperty())
		{
			return FunctionKind.SETTER;
		}
		if(isConstructor())
		{
			return FunctionKind.CONSTRUCTOR;
		}
		return FunctionKind.SIMPLE;
	}

	@Override
	public boolean isDeprecated()
	{
		final T stub = getStub();
		if(stub != null)
		{
			return stub.isDeprecated();
		}
		return JSDocumentationUtils.calculateDeprecated(this);
	}

	@Override
	public boolean isReferencesArguments()
	{
		final T stub = getStub();
		if(stub != null)
		{
			return stub.isReferencesArguments();
		}

		if(!referencesArgumentsCalculated)
		{
			acceptChildren(new JSElementVisitor()
			{
				boolean continueVisiting = true;

				@Override
				public void visitJSReferenceExpression(final JSReferenceExpression node)
				{
					if(isInJS(node) && node.getQualifier() == null)
					{
						if("arguments".equals(node.getText()))
						{
							referencesArguments = true;
							continueVisiting = false;
							return;
						}
					}
					super.visitJSReferenceExpression(node);
				}

				@Override
				public void visitJSElement(final JSElement node)
				{
					if(continueVisiting)
					{
						node.acceptChildren(this);
					}
				}
			});

			referencesArgumentsCalculated = true;
		}

		return referencesArguments;
	}

	private static boolean isInJS(final JSReferenceExpression node)
	{
		final PsiElement parent = node.getParent();
		if(parent != null && !(parent.getLanguage() instanceof JavascriptLanguage))
		{
			return false;
		}
		return true;
	}

	@Override
	public PsiElement getNameIdentifier()
	{
		final ASTNode node = findNameIdentifier();
		return node != null ? node.getPsi() : null;
	}
}
