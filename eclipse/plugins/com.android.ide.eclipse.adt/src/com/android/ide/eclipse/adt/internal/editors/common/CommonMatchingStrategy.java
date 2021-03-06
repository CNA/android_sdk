/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Eclipse Public License, Version 1.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.eclipse.org/org/documents/epl-v10.php
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.ide.eclipse.adt.internal.editors.common;

import com.android.ide.common.resources.ResourceFolder;
import com.android.ide.eclipse.adt.AdtPlugin;
import com.android.ide.eclipse.adt.internal.editors.layout.LayoutEditorMatchingStrategy;
import com.android.ide.eclipse.adt.internal.resources.manager.ResourceManager;
import com.android.resources.ResourceFolderType;

import org.eclipse.core.resources.IFile;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorMatchingStrategy;
import org.eclipse.ui.IEditorReference;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.part.FileEditorInput;

/**
 * Matching strategy for the editors.
 * This finds the right MatchingStrategy and delegates to it.
 */
public class CommonMatchingStrategy implements IEditorMatchingStrategy {

    @Override
    public boolean matches(IEditorReference editorRef, IEditorInput input) {
        if (input instanceof FileEditorInput) {
            FileEditorInput fileInput = (FileEditorInput)input;

            // get the IFile object and check it's in one of the layout folders.
            IFile iFile = fileInput.getFile();
            ResourceFolder resFolder = ResourceManager.getInstance().getResourceFolder(iFile);

            if (resFolder != null && resFolder.getType() == ResourceFolderType.LAYOUT) {

                LayoutEditorMatchingStrategy m = new LayoutEditorMatchingStrategy();
                return m.matches(editorRef, fileInput);
            } else {
                // Per the IEditorMatchingStrategy documentation, editorRef.getEditorInput()
                // is expensive so try exclude files that definitely don't match, such
                // as those with the wrong extension or wrong file name
                if (iFile.getName().equals(editorRef.getName()) &&
                        editorRef.getId().equals(CommonXmlEditor.ID)) {
                    try {
                        return input.equals(editorRef.getEditorInput());
                    } catch (PartInitException e) {
                        AdtPlugin.log(e, null);
                    }
                }
            }
        }
        return false;
    }
}
