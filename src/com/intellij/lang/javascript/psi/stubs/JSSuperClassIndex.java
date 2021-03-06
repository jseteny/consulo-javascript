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

package com.intellij.lang.javascript.psi.stubs;

import org.jetbrains.annotations.NotNull;
import com.intellij.lang.javascript.psi.JSClass;
import com.intellij.lang.javascript.types.JSFileElementType;
import com.intellij.psi.stubs.StringStubIndexExtension;
import com.intellij.psi.stubs.StubIndexKey;

public class JSSuperClassIndex extends StringStubIndexExtension<JSClass>
{
	public static final StubIndexKey<String, JSClass> KEY = StubIndexKey.createIndexKey("JS.class.super");
	private static final int VERSION = 1;

	@NotNull
	@Override
	public StubIndexKey<String, JSClass> getKey()
	{
		return KEY;
	}

	@Override
	public int getVersion()
	{
		return super.getVersion() + VERSION + JSFileElementType.VERSION;
	}
}