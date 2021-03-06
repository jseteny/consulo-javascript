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

package com.intellij.lang.javascript.structureView;

import java.util.Comparator;
import java.util.List;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.structureView.TextEditorBasedStructureViewModel;
import com.intellij.ide.util.FileStructureFilter;
import com.intellij.ide.util.treeView.smartTree.ActionPresentation;
import com.intellij.ide.util.treeView.smartTree.ActionPresentationData;
import com.intellij.ide.util.treeView.smartTree.Filter;
import com.intellij.ide.util.treeView.smartTree.Grouper;
import com.intellij.ide.util.treeView.smartTree.Sorter;
import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.lang.javascript.index.JSNamedElementProxy;
import com.intellij.lang.javascript.index.JavaScriptIndex;
import com.intellij.lang.javascript.psi.*;
import com.intellij.lang.javascript.psi.resolve.VariantsProcessor;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.util.PsiTreeUtil;

/**
 * @by max, maxim
 */
public class JSStructureViewModel extends TextEditorBasedStructureViewModel
{
	private PsiElement myRoot;
	private Filter[] myFilters = new Filter[]{
			ourFieldsFilter,
			ourInheritedFilter
	};

	@NonNls
	private static final String ID = "KIND";
	private static Sorter ourKindSorter = new Sorter()
	{
		private Comparator myComparator = new Comparator()
		{
			@Override
			public int compare(final Object o, final Object o2)
			{
				return getWeight(o) - getWeight(o2);
			}

			private int getWeight(final Object s)
			{
				if(s instanceof JSSuperGroup)
				{
					return 5;
				}
				Object o = ((StructureViewTreeElement) s).getValue();

				JSNamedElementProxy.NamedItemType type = null;
				if(o instanceof JSNamedElementProxy)
				{
					type = ((JSNamedElementProxy) o).getType();
				}

				if(o instanceof JSProperty)
				{
					JSElement propertyValue = ((JSProperty) o).getValue();
					if(propertyValue instanceof JSFunction)
					{
						o = propertyValue;
					}
				}

				if(o instanceof JSFunction ||
						type == JSNamedElementProxy.NamedItemType.ImplicitFunction ||
						type == JSNamedElementProxy.NamedItemType.FunctionExpression ||
						type == JSNamedElementProxy.NamedItemType.Function ||
						type == JSNamedElementProxy.NamedItemType.MemberFunction ||
						type == JSNamedElementProxy.NamedItemType.FunctionProperty)
				{
					return 10;
				}

				if(o instanceof JSVariable ||
						o instanceof JSProperty ||
						type == JSNamedElementProxy.NamedItemType.ImplicitVariable ||
						type == JSNamedElementProxy.NamedItemType.Variable ||
						type == JSNamedElementProxy.NamedItemType.Property ||
						type == JSNamedElementProxy.NamedItemType.MemberVariable ||
						type == JSNamedElementProxy.NamedItemType.Definition ||
						type == JSNamedElementProxy.NamedItemType.AttributeValue)
				{
					return 20;
				}

				if(o instanceof VariantsProcessor.MyElementWrapper)
				{
					return 6;
				}
				if(o instanceof JSClass || type == JSNamedElementProxy.NamedItemType.Clazz)
				{
					return 7;
				}
				return 30;
			}
		};

		@Override
		public Comparator getComparator()
		{
			return myComparator;
		}

		@Override
		public boolean isVisible()
		{
			return false;
		}

		@Override
		@NotNull
		public ActionPresentation getPresentation()
		{
			return null; // will not be shown
		}

		@Override
		@NotNull
		public String getName()
		{
			return ID;
		}
	};

	private Sorter[] mySorters = new Sorter[]{
			ourKindSorter,
			Sorter.ALPHA_SORTER
	};

	private static Filter ourFieldsFilter = new Filter()
	{
		@NonNls
		public static final String ID = "SHOW_FIELDS";

		@Override
		public boolean isVisible(TreeElement treeNode)
		{
			if(!(treeNode instanceof JSStructureViewElement))
			{
				return true;
			}
			final PsiElement element = ((JSStructureViewElement) treeNode).getRealElement();

			if(element instanceof JSClass)
			{
				return true;
			}

			return element instanceof JSFunction ||
					(element instanceof JSProperty && ((JSProperty) element).getValue() instanceof JSFunction) ||
					element instanceof JSObjectLiteralExpression;
		}

		@Override
		public boolean isReverted()
		{
			return true;
		}

		@Override
		@NotNull
		public ActionPresentation getPresentation()
		{
			return new ActionPresentationData(IdeBundle.message("action.structureview.show.fields"), null, AllIcons.Nodes.Variable);
		}

		@Override
		@NotNull
		public String getName()
		{
			return ID;
		}
	};

	private static final Filter ourInheritedFilter = new FileStructureFilter()
	{
		@NonNls
		public static final String ID = "SHOW_INHERITED";

		@Override
		public boolean isVisible(TreeElement treeNode)
		{
			if(treeNode instanceof JSStructureViewElement)
			{
				return !((JSStructureViewElement) treeNode).isInherited();
			}
			else
			{
				return true;
			}
		}

		@Override
		@NotNull
		public ActionPresentation getPresentation()
		{
			return new ActionPresentationData(IdeBundle.message("action.structureview.show.inherited"), null, IconLoader.getIcon("/hierarchy/supertypes" +
					".png"));
		}

		@Override
		@NotNull
		public String getName()
		{
			return ID;
		}

		@Override
		public boolean isReverted()
		{
			return true;
		}

		@Override
		public String getCheckBoxText()
		{
			return IdeBundle.message("file.structure.toggle.show.inherited");
		}

		@Override
		public Shortcut[] getShortcut()
		{
			return KeymapManager.getInstance().getActiveKeymap().getShortcuts("FileStructurePopup");
		}
	};

	private static Grouper myInheritedGrouper = new JSSuperGrouper();

	private Grouper[] myGroupers = new Grouper[]{
			myInheritedGrouper
	};
	private final Class[] myClasses = new Class[]{
			JSFunction.class,
			JSVariable.class,
			JSDefinitionExpression.class,
			JSClass.class,
			JSProperty.class
	};

	public JSStructureViewModel(final PsiElement root)
	{
		super(root.getContainingFile());
		myRoot = root;
	}

	public JSStructureViewModel(PsiElement root, final Editor editor)
	{
		super(editor);
		myRoot = root;
	}

	@Override
	@NotNull
	public StructureViewTreeElement getRoot()
	{
		return new JSStructureViewElement(myRoot);
	}

	@Override
	@NotNull
	public Grouper[] getGroupers()
	{
		return myGroupers;
	}

	@Override
	@NotNull
	public Sorter[] getSorters()
	{
		return mySorters;
	}

	@Override
	@NotNull
	public Filter[] getFilters()
	{
		return myFilters;
	}

	@Override
	protected boolean isSuitable(final PsiElement element)
	{
		return super.isSuitable(element) && (!(element instanceof JSVariable) || PsiTreeUtil.getParentOfType(element, JSFunction.class) == null);
	}

	@Override
	public Object getCurrentEditorElement()
	{
		Object editorElement = super.getCurrentEditorElement();

		final PsiFile file = getPsiFile();
		if(editorElement == null && !(file instanceof JSFile))
		{
			final int offset = getEditor().getCaretModel().getOffset();
			final PsiElement at = file.findElementAt(offset);
			final PsiLanguageInjectionHost injectionHost = PsiTreeUtil.getParentOfType(at, PsiLanguageInjectionHost.class);

			if(injectionHost != null)
			{
				final Ref<PsiElement> ref = new Ref<PsiElement>();
				InjectedLanguageUtil.enumerate(injectionHost, new PsiLanguageInjectionHost.InjectedPsiVisitor()
				{
					@Override
					public void visit(@NotNull final PsiFile injectedPsi, @NotNull final List<PsiLanguageInjectionHost.Shred> places)
					{
						final PsiLanguageInjectionHost.Shred shred = places.get(0);
						final int injectedStart = shred.getRangeInsideHost().getStartOffset() + shred.getHost().getTextOffset();
						final int offsetInInjected = offset - injectedStart;

						ref.set(injectedPsi.findElementAt(offsetInInjected));
					}
				});

				final PsiElement element = ref.get();
				if(element != null)
				{
					editorElement = findAcceptableElement(element);
					return editorElement;
				}
			}
		}

		if(editorElement instanceof JSDefinitionExpression)
		{
			final PsiElement element = ((PsiElement) editorElement).getParent();

			if(element instanceof JSAssignmentExpression)
			{
				final JSExpression roperand = ((JSAssignmentExpression) element).getROperand();
				if(roperand instanceof JSFunctionExpression)
				{
					editorElement = roperand;
				}
			}
		}

		if(editorElement instanceof JSNamedElement)
		{
			final PsiFile containingFile = ((PsiElement) editorElement).getContainingFile();
			final PsiElement context = containingFile.getContext();
			final int offset = ((PsiElement) editorElement).getTextOffset();
			final JavaScriptIndex scriptIndex = JavaScriptIndex.getInstance(containingFile.getProject());
			final PsiElement element;

			if(context != null)
			{
				element = scriptIndex.findSymbolWithNameAndOffsetInEntry(scriptIndex.getIndexOf(((JSNamedElement) editorElement).getName()), offset,
						scriptIndex.getEntryForFile(containingFile));
			}
			else
			{
				element = scriptIndex.findSymbolByFileAndNameAndOffset(containingFile.getVirtualFile().getPath(), ((JSNamedElement) editorElement).getName(),
						offset);
			}
			if(element != null)
			{
				editorElement = element;
			}
		}
		return editorElement;
	}

	@Override
	protected PsiFile getPsiFile()
	{
		return myRoot.getContainingFile();
	}

	@Override
	@NotNull
	protected Class[] getSuitableClasses()
	{
		return myClasses;
	}

	public void setFilters(final Filter[] filters)
	{
		myFilters = filters;
	}

	public void setGroupers(final Grouper[] groupers)
	{
		myGroupers = groupers;
	}

	public void setSorters(final Sorter[] sorters)
	{
		mySorters = sorters;
	}

	@Override
	public boolean shouldEnterElement(final Object element)
	{
		return shouldEnterElementStatic(element);
	}

	public static boolean shouldEnterElementStatic(final Object element)
	{
		return element instanceof JSClass || (element instanceof JSNamedElementProxy && ((JSNamedElementProxy) element).getType() == JSNamedElementProxy
				.NamedItemType.Clazz);
	}
}
