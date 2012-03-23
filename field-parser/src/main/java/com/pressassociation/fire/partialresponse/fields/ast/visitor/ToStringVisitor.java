package com.pressassociation.fire.partialresponse.fields.ast.visitor;

import com.pressassociation.fire.partialresponse.fields.ast.*;

/**
 * Generated JavaDoc Comment.
 *
 * @author Matt Nathan
 */
public class ToStringVisitor extends TransformingVisitor<String> {
  @SuppressWarnings("StringBufferField")
  private final StringBuilder buffer = new StringBuilder();

  @Override
  protected void afterSubSelectionFields(SubSelection subSelection) {
    buffer.append(')');
  }

  @Override
  protected boolean beforePathField(Path path) {
    buffer.append('/');
    return true;
  }

  @Override
  protected boolean beforeFieldsNext(Fields fields) {
    buffer.append(',');
    return true;
  }

  @Override
  protected boolean beforeSubSelectionFields(SubSelection subSelection) {
    buffer.append('(');
    return true;
  }

  @Override
  public void visitWildcard(Wildcard wildcard) {
    buffer.append('*');
  }

  @Override
  public void visitWord(Word word) {
    buffer.append(word.getStringValue());
  }

  @Override
  public String getResult() {
    return buffer.toString();
  }
}