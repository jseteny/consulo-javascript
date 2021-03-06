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

package com.intellij.lang.javascript.psi;

import com.intellij.lang.javascript.psi.stubs.JSFunctionStub;
import com.intellij.psi.PsiElement;
import com.intellij.psi.StubBasedPsiElement;
import com.intellij.util.ArrayFactory;

/**
 * @author max
 */
public interface JSFunction extends JSQualifiedNamedElement, JSSourceElement, JSAttributeListOwner, StubBasedPsiElement<JSFunctionStub>
{
	JSFunction[] EMPTY_ARRAY = new JSFunction[0];
	ArrayFactory<JSFunction> ARRAY_FACTORY = new ArrayFactory<JSFunction>()
	{
		@Override
		public JSFunction[] create(int count)
		{
			return count == 0 ? EMPTY_ARRAY : new JSFunction[count];
		}
	};

	JSParameterList getParameterList();

	JSSourceElement[] getBody();

	boolean isGetProperty();

	boolean isSetProperty();

	boolean isConstructor();

	String getReturnTypeString();

	PsiElement getReturnTypeElement();

	enum FunctionKind
	{
		GETTER, SETTER, CONSTRUCTOR, SIMPLE
	}

	FunctionKind getKind();

	boolean isDeprecated();

	boolean isReferencesArguments();
}
