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
import org.jetbrains.annotations.Nullable;
import com.intellij.lang.ASTNode;
import com.intellij.lang.LanguageNamesValidation;
import com.intellij.lang.javascript.JSElementTypes;
import com.intellij.lang.javascript.JSTokenTypes;
import com.intellij.lang.javascript.JavaScriptSupportLoader;
import com.intellij.lang.javascript.index.JSNamedElementProxy;
import com.intellij.lang.javascript.index.JSTypeEvaluateManager;
import com.intellij.lang.javascript.index.JavaScriptIndex;
import com.intellij.lang.javascript.psi.*;
import com.intellij.lang.javascript.psi.resolve.BaseJSSymbolProcessor;
import com.intellij.lang.javascript.psi.resolve.JSResolveUtil;
import com.intellij.lang.javascript.psi.resolve.ResolveProcessor;
import com.intellij.lang.javascript.psi.resolve.VariantsProcessor;
import com.intellij.lang.javascript.psi.resolve.WalkUpResolveProcessor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.ResolveResult;
import com.intellij.psi.ResolveState;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlToken;
import com.intellij.refactoring.rename.BindablePsiReference;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;

public class JSReferenceExpressionImpl extends JSExpressionImpl implements JSReferenceExpression, BindablePsiReference
{
	private static final TokenSet IDENTIFIER_TOKENS_SET = TokenSet.orSet(JSTokenTypes.IDENTIFIER_TOKENS_SET,
			TokenSet.create(JSTokenTypes.ANY_IDENTIFIER));

	public JSReferenceExpressionImpl(final ASTNode node)
	{
		super(node);
	}

	@Override
	@Nullable
	public JSExpression getQualifier()
	{
		final ASTNode node = getNode().findChildByType(JSElementTypes.EXPRESSIONS);
		return node != null ? (JSExpression) node.getPsi() : null;
	}

	@Override
	@Nullable
	public String getReferencedName()
	{
		final ASTNode nameElement = getNameElement();
		return nameElement != null ? nameElement.getText() : null;
	}

	@Override
	@Nullable
	public PsiElement getReferenceNameElement()
	{
		final ASTNode element = getNameElement();
		return element != null ? element.getPsi() : null;
	}

	@Override
	public PsiElement getElement()
	{
		return this;
	}

	@Override
	public PsiReference getReference()
	{
		return this;
	}

	@Override
	public TextRange getRangeInElement()
	{
		final ASTNode nameElement = getNameElement();
		final int startOffset = nameElement != null ? nameElement.getStartOffset() : getNode().getTextRange().getEndOffset();
		return new TextRange(startOffset - getNode().getStartOffset(), getTextLength());
	}

	private ASTNode getNameElement()
	{
		return getNode().findChildByType(IDENTIFIER_TOKENS_SET);
	}

	@Override
	public PsiElement resolve()
	{
		final ResolveResult[] resolveResults = multiResolve(true);

		return resolveResults.length == 0 || resolveResults.length > 1 ? null : resolveResults[0].getElement();
	}

	@Override
	public String getCanonicalText()
	{
		return getText();
	}

	@Override
	public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException
	{
		final ResolveResult[] results = multiResolve(false);

		for(ResolveResult r : results)
		{
			PsiElement element = r.getElement();

			if(element instanceof JSNamedElementProxy)
			{
				JSNamedElementProxy.NamedItemType namedItemType = ((JSNamedElementProxy) element).getType();

				if(namedItemType == JSNamedElementProxy.NamedItemType.AttributeValue)
				{
					return handleElementRenameInternal(newElementName);
				}
			}
		}

		throw new IncorrectOperationException("Unexpected rename request"); // bindToElement should be called
	}

	PsiElement handleElementRenameInternal(String newElementName) throws IncorrectOperationException
	{
		final int i = newElementName.lastIndexOf('.');
		if(i != -1)
		{
			newElementName = newElementName.substring(0, i);
		}
		if(!LanguageNamesValidation.INSTANCE.forLanguage(JavaScriptSupportLoader.JAVASCRIPT.getLanguage()).isIdentifier(newElementName, null))
		{
			throw new IncorrectOperationException("Invalid javascript element name:" + newElementName);
		}
		final PsiElement parent = getParent();
		if(parent instanceof JSClass || parent instanceof JSFunction)
		{
			final ASTNode node = ((JSNamedElement) parent).findNameIdentifier();
			if(node != null && node.getPsi() == this)
			{
				return this; // JSNamedElement.setName will care of things
			}
		}
		JSChangeUtil.doIdentifierReplacement(this, getNameElement().getPsi(), newElementName);
		return getParent();
	}

	@Override
	public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException
	{
		final PsiElement parent = getParent();

		if(parent instanceof JSClass ||
				parent instanceof JSNamespaceDeclaration ||
				parent instanceof JSFunction)
		{
			final ASTNode node = ((JSNamedElement) parent).findNameIdentifier();

			if(node != null && node.getPsi() == this)
			{
				if(parent == element || element instanceof PsiFile)
				{
					return this; // JSNamedElement.setName will care of things
				}
			}
		}

		String qName = JSPsiImplUtils.getQNameForMove(this, element);

		if(qName != null)
		{
			ASTNode newChild = JSChangeUtil.createExpressionFromText(getProject(), qName);
			getParent().getNode().replaceChild(getNode(), newChild);
			return newChild.getPsi();
		}

		String newName = ((PsiNamedElement) element).getName();
		if(element instanceof PsiFile)
		{
			int index = newName.lastIndexOf('.');
			if(index != -1)
			{
				newName = newName.substring(0, index);
			}
		}

		final ASTNode nameElement = JSChangeUtil.createNameIdentifier(getProject(), newName);
		getNode().replaceChild(getNameElement(), nameElement);
		return this;
	}

	@Override
	public boolean isReferenceTo(PsiElement element)
	{
		if(element instanceof PsiNamedElement || element instanceof XmlAttributeValue)
		{
			final String referencedName = getReferencedName();

			if(referencedName != null)
			{
				if(element instanceof JSDefinitionExpression && referencedName.equals(((JSDefinitionExpression) element).getName()))
				{
					final JSExpression expression = ((JSDefinitionExpression) element).getExpression();
					if(expression instanceof JSReferenceExpression)
					{
						final JSReferenceExpression jsReferenceExpression = (JSReferenceExpression) expression;
						final JSExpression qualifier = jsReferenceExpression.getQualifier();
						final JSExpression myQualifier = getQualifier();

						return (myQualifier != null || (qualifier == myQualifier || "window".equals(qualifier.getText())));
					}
					else
					{
						return true;
					}
				}
				else if(element instanceof JSProperty && referencedName.equals(((JSProperty) element).getName()))
				{
					if(getQualifier() != null)
					{
						return true; // TODO: check for type of element to be the same
					}
					//return false;
				}
			}
			return JSResolveUtil.isReferenceTo(this, referencedName, element);
		}
		return false;
	}

	private void doProcessLocalDeclarations(final JSExpression qualifier, final ResolveProcessor processor, boolean ecma, boolean completion)
	{
		final JSClass jsClass = findEnclosingClass(this);
		processor.configureClassScope(jsClass);

		final boolean inTypeContext = JSResolveUtil.isExprInTypeContext(this);
		final boolean whereTypeCanBe = inTypeContext || (completion && ecma ? JSResolveUtil.isInPlaceWhereTypeCanBeDuringCompletion(this) : false);
		PsiElement elToProcess = this;
		PsiElement scopeToStopAt = null;

		final PsiElement parent = getParent();
		boolean strictClassOffset = JSResolveUtil.getTopReferenceParent(parent) instanceof JSImportStatement;
		boolean toProcessMembers = !strictClassOffset;

		if(qualifier != null)
		{
			elToProcess = jsClass;

			if(jsClass == null)
			{
				if(qualifier instanceof JSThisExpression)
				{
					if(ecma)
					{
						final JSFunction nearestFunction = PsiTreeUtil.getParentOfType(this, JSFunction.class);
						elToProcess = nearestFunction != null ? nearestFunction : this;
					}
					else
					{
						elToProcess = PsiTreeUtil.getParentOfType(this, JSProperty.class);
						if(elToProcess != null)
						{
							scopeToStopAt = elToProcess.getParent();
						}
					}
				}
				else if(qualifier instanceof JSSuperExpression)
				{
					elToProcess = JSResolveUtil.getClassFromTagNameInMxml(this);
				}
			}
		}
		else if(whereTypeCanBe)
		{
			if(inTypeContext)
			{
				if(!(parent instanceof JSNewExpression) &&
						!(parent instanceof JSAttributeList) &&
						!(parent instanceof JSBinaryExpression))
				{
					toProcessMembers = false;
					// get function since it can have imports
					final JSFunction nearestFunction = PsiTreeUtil.getParentOfType(this, JSFunction.class);
					elToProcess = nearestFunction != null ? nearestFunction.getFirstChild() : jsClass;
				}
			}
			else if(parent instanceof JSExpressionStatement && JSResolveUtil.isPlaceWhereNsCanBe(parent))
			{
				toProcessMembers = false;
				elToProcess = null;
			}
		}

		if((qualifier instanceof JSThisExpression || qualifier instanceof JSSuperExpression) && jsClass != null)
		{
			scopeToStopAt = jsClass;
			if(ecma)
			{
				JSFunctionExpression expression = PsiTreeUtil.getParentOfType(this, JSFunctionExpression.class);
				if(expression != null)
				{
					elToProcess = expression.getFirstChild();
				}
			}
		}

		if(elToProcess == null && whereTypeCanBe)
		{
			elToProcess = PsiTreeUtil.getParentOfType(this, JSPackageStatement.class, JSFile.class);
			if(elToProcess != null)
			{
				elToProcess = PsiTreeUtil.getChildOfType(elToProcess, PsiWhiteSpace.class);  // this is hack, get rid of it
				if(elToProcess == null)
				{
					elToProcess = this;
				}
			}
		}

		processor.setTypeContext(whereTypeCanBe || (qualifier == null && parent instanceof JSReferenceExpression) || strictClassOffset);
		processor.setToProcessMembers(toProcessMembers);

		if(elToProcess != null)
		{
			processor.setToProcessHierarchy(qualifier != null || !inTypeContext);
			processor.setToSkipClassDeclarationsOnce(qualifier instanceof JSSuperExpression);
			JSResolveUtil.treeWalkUp(processor, elToProcess, elToProcess, this, scopeToStopAt);

			processor.setToProcessHierarchy(false);
			processor.setToSkipClassDeclarationsOnce(false);
		}
	}

	private static
	@Nullable
	JSClass findEnclosingClass(PsiElement elt)
	{
		JSClass jsClass = PsiTreeUtil.getParentOfType(elt, JSClass.class);
		if(jsClass == null && elt != null)
		{
			final PsiElement element = JSResolveUtil.getClassReferenceForXmlFromContext(elt.getContainingFile());
			if(element instanceof JSClass)
			{
				jsClass = (JSClass) element;
			}
		}
		return jsClass;
	}

	@Override
	public Object[] getVariants()
	{
		final PsiFile containingFile = getContainingFile();
		final boolean ecma = containingFile.getLanguage() == JavaScriptSupportLoader.ECMA_SCRIPT_L4;
		Object[] smartVariants = JSSmartCompletionVariantsHandler.getSmartVariants(this, ecma);
		if(smartVariants != null)
		{
			return smartVariants;
		}
		final JSExpression qualifier = getResolveQualifier();

		ResolveProcessor localProcessor;
		final JavaScriptIndex index = JavaScriptIndex.getInstance(getProject());

		if(isLocalResolveQualifier(qualifier))
		{
			if(JSResolveUtil.isSelfReference(getParent(), this))
			{ // Prevent Rulezz to appear
				return ArrayUtil.EMPTY_OBJECT_ARRAY;
			}

			localProcessor = new ResolveProcessor(null, this);

			doProcessLocalDeclarations(qualifier, localProcessor, ecma, true);
		}
		else
		{
			final MyTypeProcessor processor = new MyTypeProcessor(null, ecma, this);
			BaseJSSymbolProcessor.doEvalForExpr(BaseJSSymbolProcessor.getOriginalQualifier(qualifier), containingFile, processor);

			if(processor.resolved == MyTypeProcessor.TypeResolveState.Resolved ||
					processor.resolved == MyTypeProcessor.TypeResolveState.Undefined ||
					(processor.resolved == MyTypeProcessor.TypeResolveState.PrefixUnknown && ecma))
			{
				String qualifiedNameToSkip = null;
				if(JSResolveUtil.isSelfReference(getParent(), this))
				{
					PsiElement originalParent = PsiUtilBase.getOriginalElement(getParent(), JSQualifiedNamedElement.class);
					if(originalParent instanceof JSQualifiedNamedElement)
					{
						qualifiedNameToSkip = ((JSQualifiedNamedElement) originalParent).getQualifiedName();
					}
				}
				return processor.getResultsAsObjects(qualifiedNameToSkip);
			}
			else
			{
				localProcessor = processor;
			}
		}

		//if (ecma && false && !localProcessor.isEncounteredDynamicClasses()) {
		//  return localProcessor.getResultsAsObjects();
		//}

		final VariantsProcessor processor = new VariantsProcessor(null, containingFile, false, this);

		if(localProcessor != null)
		{
			processor.addLocalResults(localProcessor.getResults());
		}

		index.processAllSymbols(processor);

		return processor.getResult();
	}

	@Override
	public boolean isSoft()
	{
		return false;
	}

	@Override
	public void accept(@NotNull PsiElementVisitor visitor)
	{
		if(visitor instanceof JSElementVisitor)
		{
			((JSElementVisitor) visitor).visitJSReferenceExpression(this);
		}
		else
		{
			visitor.visitElement(this);
		}
	}

	@Override
	@NotNull
	public ResolveResult[] multiResolve(final boolean incompleteCode)
	{
		return JSResolveUtil.resolve(getContainingFile(), this, MyResolver.INSTANCE);
	}

	static class MyResolver implements JSResolveUtil.Resolver<JSReferenceExpressionImpl>
	{
		private static final MyResolver INSTANCE = new MyResolver();

		@Override
		public ResolveResult[] doResolve(final JSReferenceExpressionImpl jsReferenceExpression, PsiFile file)
		{
			return jsReferenceExpression.doResolve(file);
		}
	}

	private ResolveResult[] doResolve(PsiFile containingFile)
	{
		final String referencedName = getReferencedName();
		if(referencedName == null)
		{
			return ResolveResult.EMPTY_ARRAY;
		}

		final PsiElement parent = getParent();
		final JSExpression qualifier = getResolveQualifier();
		final boolean ecma = containingFile.getLanguage().isKindOf(JavaScriptSupportLoader.ECMA_SCRIPT_L4);
		final boolean localResolve = isLocalResolveQualifier(qualifier);
		final boolean parentIsDefinition = parent instanceof JSDefinitionExpression;

		final JavaScriptIndex index = JavaScriptIndex.getInstance(containingFile.getProject());

		// Handle self references
		PsiElement currentParent = JSResolveUtil.getTopReferenceParent(parent);
		if(JSResolveUtil.isSelfReference(currentParent, this))
		{
			if(!(currentParent instanceof JSPackageStatement) || parent == currentParent)
			{
				return new ResolveResult[]{new JSResolveUtil.MyResolveResult(currentParent)};
			}
		}

		JSExpression realQualifier = getQualifier();
		if(isE4XAttributeReference(realQualifier))
		{ // TODO: fix tree
			return new ResolveResult[]{new JSResolveUtil.MyResolveResult(this)};
		}

		if("*".equals(referencedName) && currentParent instanceof JSImportStatement && qualifier instanceof JSReferenceExpression)
		{ // TODO: move to some processor
			return ((JSReferenceExpression) qualifier).multiResolve(false);
		}

		ResolveProcessor localProcessor;

		if(localResolve)
		{
			localProcessor = new ResolveProcessor(referencedName, this);

			final boolean canResolveAllLocally = !parentIsDefinition || !ecma;
			doProcessLocalDeclarations(realQualifier, localProcessor, ecma, false);

			if(canResolveAllLocally)
			{
				final PsiElement jsElement = localProcessor.getResult();

				if(jsElement != null || (qualifier != null && ecma && localProcessor.foundAllValidResults()))
				{
					return localProcessor.getResultsAsResolveResults();
				}
			}
		}
		else
		{
			final MyTypeProcessor processor = new MyTypeProcessor(referencedName, ecma, this);
			BaseJSSymbolProcessor.doEvalForExpr(qualifier, containingFile, processor);

			if(processor.resolved == MyTypeProcessor.TypeResolveState.PrefixUnknown && ecma)
			{
				return new ResolveResult[]{new JSResolveUtil.MyResolveResult(this)};
			}

			if(processor.resolved == MyTypeProcessor.TypeResolveState.Resolved ||
					processor.resolved == MyTypeProcessor.TypeResolveState.Undefined ||
					processor.getResult() != null)
			{
				return processor.getResultsAsResolveResults();
			}
			else
			{
				localProcessor = processor;
			}
		}

		//if(ecma && false && !localProcessor.isEncounteredDynamicClasses()) {
		//  return localProcessor.getResultsAsResolveResults();
		//}

		ResolveResult[] results = doOldResolve(containingFile, referencedName, parent, qualifier, ecma, localResolve, parentIsDefinition, index,
				localProcessor);

		return results;
	}

	private boolean isE4XAttributeReference(JSExpression realQualifier)
	{
		return getNode().findChildByType(JSTokenTypes.AT) != null || (realQualifier != null && realQualifier.getNode().findChildByType(JSTokenTypes.AT) !=
				null);
	}

	@Nullable
	public JSExpression getResolveQualifier()
	{
		final JSExpression qualifier = getQualifier();

		if(qualifier instanceof JSReferenceExpression)
		{
			final ASTNode astNode = getNode();
			ASTNode selection = astNode.getTreeNext();
			// TODO:this is not accurate
			if(selection != null && selection.getElementType() == JSTokenTypes.COLON_COLON)
			{
				return null;
			}

			final ASTNode nsSelection = astNode.findChildByType(JSTokenTypes.COLON_COLON);
			if(nsSelection != null)
			{
				return ((JSReferenceExpressionImpl) qualifier).getResolveQualifier();
			}
		}
		else if(qualifier == null)
		{
			final ASTNode node = getNode().getFirstChildNode();

			if(node.getElementType() == JSTokenTypes.AT)
			{
				PsiElement parent = getParent();
				if(parent instanceof JSBinaryExpression)
				{
					PsiElement element = parent.getParent().getParent();
					if(element instanceof JSCallExpression)
					{
						parent = ((JSCallExpression) element).getMethodExpression();
					}
				}
				if(parent instanceof JSExpression)
				{
					return (JSExpression) parent;
				}
				return null;
			}
		}
		return qualifier;
	}

	private ResolveResult[] doOldResolve(final PsiFile containingFile, final String referencedName, final PsiElement parent,
			final JSExpression qualifier, final boolean ecma, final boolean localResolve, final boolean parentIsDefinition, final JavaScriptIndex index,
			ResolveProcessor localProcessor)
	{
		if(parentIsDefinition && ((ecma && !localResolve) || (!ecma && qualifier != null)))
		{
			return new ResolveResult[]{new JSResolveUtil.MyResolveResult(parent)};
		}

		if(localResolve && parentIsDefinition && ecma)
		{
			if(!localProcessor.processingEncounteredAbsenceOfTypes())
			{
				return localProcessor.getResultsAsResolveResults();
			}

			// Fallback for finding some assignment in global scope
		}

		final WalkUpResolveProcessor processor = new WalkUpResolveProcessor(index.getIndexOf(referencedName), null, containingFile, false, this);

		if(localProcessor != null)
		{
			processor.addLocalResults(localProcessor.getResultsAsResolveResults());
		}

		JavaScriptIndex.getInstance(containingFile.getProject()).processAllSymbols(processor);
		return processor.getResults();
	}

	private static boolean isLocalResolveQualifier(final JSExpression qualifier)
	{
		return qualifier == null || qualifier instanceof JSThisExpression || qualifier instanceof JSSuperExpression;
	}

	@Override
	public boolean shouldCheckReferences()
	{
		return true;
	}

	private static class MyTypeProcessor extends ResolveProcessor implements BaseJSSymbolProcessor.TypeProcessor

	{
		private final boolean myEcma;

		public MyTypeProcessor(String referenceName, final boolean ecma, PsiElement _place)
		{
			super(referenceName, _place);
			myEcma = ecma;
			setToProcessHierarchy(true);

			configureClassScope(findEnclosingClass(_place));
		}

		enum TypeResolveState
		{
			Unknown, Resolved, Undefined, PrefixUnknown
		}

		TypeResolveState resolved = TypeResolveState.Unknown;

		@Override
		public void process(String type, @NotNull final BaseJSSymbolProcessor.EvaluateContext evaluateContext, PsiElement source)
		{
			if(evaluateContext.visitedTypes.contains(type))
			{
				return;
			}
			evaluateContext.visitedTypes.add(type);

			if("*".equals(type) || "Object".equals(type))
			{
				return;
			}

			if(JSTypeEvaluateManager.isArrayType(type))
			{
				int index = type.indexOf('[');
				if(index != -1)
				{
					type = type.substring(0, index);
				}
			}

			PsiElement typeSource = evaluateContext.getSource();
			if(typeSource instanceof JSNamedElementProxy && ((JSNamedElementProxy) typeSource).getType() == JSNamedElementProxy.NamedItemType.Clazz)
			{
				typeSource = JSResolveUtil.unwrapProxy(typeSource);
			}

			setProcessStatics(false);

			final PsiElement placeParent = place.getParent();
			boolean setTypeContext = placeParent instanceof JSReferenceList;
			final PsiElement clazz = source != null && (source instanceof JSClass || source instanceof XmlFile) ? source : JSClassImpl.findClassFromNamespace
					(type, place);

			if(clazz instanceof JSClass)
			{
				final JSClass jsClass = (JSClass) clazz;

				if("RemoteObject".equals(jsClass.getName()) &&
						typeSource instanceof JSNamedElementProxy &&
						((JSNamedElementProxy) typeSource).getType() == JSNamedElementProxy.NamedItemType.AttributeValue)
				{
					final XmlTag tag = PsiTreeUtil.getParentOfType(((JSNamedElementProxy) typeSource).getElement(), XmlTag.class);
					for(XmlTag method : tag.findSubTags("method", tag.getNamespace()))
					{
						if(!execute(method, ResolveState.initial()))
						{
							break;
						}
					}

					resolved = TypeResolveState.Resolved;
					return;
				}

				final boolean statics = myEcma && JSPsiImplUtils.isTheSameClass(typeSource, jsClass) && !(((JSReferenceExpression) place).getQualifier()
						instanceof JSCallExpression);
				setProcessStatics(statics);
				if(statics)
				{
					setTypeName(jsClass.getQualifiedName());
				}

				final boolean saveSetTypeContext = isTypeContext();
				final boolean saveToProcessMembers = isToProcessMembers();

				if(setTypeContext)
				{
					setTypeContext(setTypeContext);
					setToProcessMembers(false);
				}

				try
				{
					final boolean b = clazz.processDeclarations(this, ResolveState.initial(), clazz, place);
					if(!b)
					{
						resolved = TypeResolveState.Resolved;
					}
					else if(myEcma)
					{
						final JSAttributeList attrList = jsClass.getAttributeList();
						if(attrList == null || !attrList.hasModifier(JSAttributeList.ModifierType.DYNAMIC))
						{
							resolved = TypeResolveState.Resolved;
						}
					}
				}
				finally
				{
					if(setTypeContext)
					{
						setTypeContext(saveSetTypeContext);
						setToProcessMembers(saveToProcessMembers);
					}
				}
			}
			else if(myEcma)
			{
				resolved = TypeResolveState.Undefined;
			}
		}

		@Override
		public boolean execute(PsiElement element, ResolveState state)
		{
			boolean b = super.execute(element, state);
			if(myEcma && getResult() != null)
			{
				resolved = MyTypeProcessor.TypeResolveState.Resolved;
			}
			return b;
		}

		@Override
		public boolean ecma()
		{
			return myEcma;
		}

		@Override
		public void setUnknownElement(@NotNull final PsiElement element)
		{
			if(!(element instanceof XmlToken))
			{
				boolean currentIsNotResolved = element == BaseJSSymbolProcessor.getOriginalQualifier(((JSReferenceExpression) place).getQualifier());
				resolved = currentIsNotResolved ? TypeResolveState.PrefixUnknown : TypeResolveState.Unknown;
			}
		}
	}
}
