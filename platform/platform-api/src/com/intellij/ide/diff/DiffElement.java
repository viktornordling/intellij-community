/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.ide.diff;

import com.intellij.openapi.diff.*;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.EncodingManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.nio.charset.Charset;

/**
 * @author Konstantin Bulenkov
 */
public abstract class DiffElement<T> {
  public static final DiffElement[] EMPTY_ARRAY = new DiffElement[0];
  private DiffPanel myDiffPanel;
  private Editor myEditor;

  public abstract String getPath();

  @NotNull
  public abstract String getName();

  public abstract long getSize();

  public abstract long getModificationStamp();

  public FileType getFileType() {
    return FileTypeManager.getInstance().getFileTypeByFileName(getName());
  }

  public abstract boolean isContainer();

  public abstract DiffElement[] getChildren() throws IOException;

  @Nullable
  public abstract DiffElement<T> findFileByRelativePath(String path);

  /**
   * Returns content data as byte array. Can be null, if element for example is a container
   * @return content byte array
   * @throws java.io.IOException when reading
   */
  @Nullable
  public abstract byte[] getContent() throws IOException;

  public Charset getCharset() {
    return EncodingManager.getInstance().getDefaultCharset();
  }

  @Nullable
  public JComponent getViewComponent(Project project) {
    disposeViewComponent();
    try {
      final T value = getValue();
      final byte[] content = getContent();
      final EditorFactory editorFactory = EditorFactory.getInstance();
      final Document document = value instanceof VirtualFile
                                ? FileDocumentManager.getInstance().getDocument((VirtualFile)value)
                                : editorFactory.createDocument(new String(content));
      if (document != null && getFileType() != null) {
        myEditor = editorFactory.createEditor(document, project, getFileType(), true);
        myEditor.getSettings().setFoldingOutlineShown(false);
        return myEditor.getComponent();
      }
    }
    catch (IOException e) {//
    }
    return null;
  }

  @Nullable
  public JComponent getDiffComponent(DiffElement element, Project project, Window parentWindow) {
    disposeDiffComponent();

    final DiffRequest request = createRequest(project, element);
    if (request != null) {
      myDiffPanel = DiffManager.getInstance().createDiffPanel(parentWindow, project);
      myDiffPanel.setRequestFocus(false);
      myDiffPanel.setDiffRequest(request);
      myDiffPanel.setTitle1(getName());
      myDiffPanel.setTitle2(element.getName());
      return myDiffPanel.getComponent();
    }

    return null;
  }

  @Nullable
  protected DiffRequest createRequest(Project project, DiffElement element) {
    final T src = getValue();
    if (src instanceof VirtualFile) {
      final Object trg = element.getValue();
      if (trg instanceof VirtualFile) {
        return SimpleDiffRequest.compareFiles((VirtualFile)src, (VirtualFile)trg, project);
      }
    }
    final DiffContent srcContent = createDiffContent();
    final DiffContent trgContent = element.createDiffContent();

    if (srcContent != null && trgContent != null) {
      final SimpleDiffRequest request = new SimpleDiffRequest(project, "");
      request.setContents(srcContent, trgContent);
      return request;
    }
    return null;
  }

  @Nullable
  protected DiffContent createDiffContent() {
    try {
      return new SimpleContent(new String(getContent(), getCharset()), getFileType());
    }
    catch (IOException e) {//
    }
    return null;
  }

  public abstract T getValue();

  public void disposeViewComponent() {
    if (myEditor != null) {
      EditorFactory.getInstance().releaseEditor(myEditor);
      myEditor = null;
    }
  }

  public void disposeDiffComponent() {
    if (myDiffPanel != null) {
      Disposer.dispose(myDiffPanel);
      myDiffPanel = null;
    }
  }

  public String getSeparator() {
    return "/";
  }

  @Nullable
  public Icon getIcon() {
    return null;
  }
}
