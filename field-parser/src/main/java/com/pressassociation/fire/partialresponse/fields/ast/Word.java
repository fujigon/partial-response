package com.pressassociation.fire.partialresponse.fields.ast;

import com.pressassociation.fire.partialresponse.fields.ast.visitor.AstVisitor;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Generated JavaDoc Comment.
 *
 * @author Matt Nathan
 */
public class Word extends Name {
  private final String stringValue;

  public Word(String stringValue) {
    checkNotNull(stringValue);
    checkArgument(!stringValue.isEmpty(), "stringValue cannot be empty");
    this.stringValue = stringValue;
  }

  public String getStringValue() {
    return stringValue;
  }

  @Override
  public void apply(AstVisitor visitor) {
    visitor.visitWord(this);
  }
}
