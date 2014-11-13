package com.pressassociation.pr.filter.json.jackson;

import com.google.common.collect.Lists;
import com.google.common.collect.Queues;
import com.google.common.io.CharStreams;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.PropertyWriter;
import com.fasterxml.jackson.databind.ser.impl.SimpleBeanPropertyFilter;
import com.pressassociation.fire.partialresponse.fields.match.Leaf;
import com.pressassociation.fire.partialresponse.fields.match.Matcher;

import java.util.Deque;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

/**
 * Jackson json property filter backed by a partial response Matcher.
 *
 * @author Matt Nathan
 */
public class JacksonMatcherFilter extends SimpleBeanPropertyFilter {
  /*
   * Implementation note:
   *
   * In order to support filtering based on a Matcher we need to be able to support the inclusions of parent elements
   * when a child should be included. Unfortunately this is not directly supported by the standard classes.
   *
   * To accomplish this we need to do two passes over the object tree. Pass one navigates to all leaf nodes of the tree
   * looking for paths that should be included in the output. Once all these are found a second pass goes through
   * writing out all included paths.
   *
   * The reason for the two paths is because the standard tree walking algorithm of jackson writes at the same time it
   * navigates. We don't know if a parent should be written until we've seen it's children, something that can't be
   * done without a no-op first pass.
   */

  private final Matcher matcher;
  private final Deque<String> propertyPath = Queues.newArrayDeque();

  // contains the current parent node for use by recursive calls.
  private Node currentNode = new Node();
  private boolean serializationMode = false;

  public JacksonMatcherFilter(Matcher matcher) {
    this.matcher = checkNotNull(matcher);
  }

  @Override
  public void serializeAsField(Object pojo, JsonGenerator jGen, SerializerProvider provider, PropertyWriter writer)
      throws Exception {
    checkNotNull(pojo);
    checkNotNull(jGen);
    checkNotNull(provider);
    checkNotNull(writer);

    // remember the node that triggered this method call
    Node parentNode = currentNode;
    propertyPath.addLast(writer.getName());
    try {
      if (!serializationMode) {
        processFirstPass(parentNode, pojo, provider, writer, jGen);
      } else {
        // do the actual writing of the field
        // if the first child in the current node matches this field then we want to write it and it's children
        if (currentNode.matchesFirstChild(writer)) {
          currentNode = currentNode.popChild();
          writer.serializeAsField(pojo, jGen, provider);
        } else {
          // write missing fields.
          writer.serializeAsOmittedField(pojo, jGen, provider);
        }
      }

      if (parentNode.isRoot()) {
        try {
          // about to enter the _real_ serialization pass, mark it as such so the above logic isn't repeated
          serializationMode = true;
          if (parentNode.isEmpty()) {
            // write out that we will be omitting data
            writer.serializeAsOmittedField(pojo, jGen, provider);
          } else {
            // in this case we've found a root property that needs to be output
            // as we go through the child nodes again we check against the Node tree instead of the Matcher, we've
            // already done those checks on the first pass
            currentNode = parentNode.popChild();
            checkState(currentNode.matchesName(writer), "Unexpected root node: %s", currentNode);
            writer.serializeAsField(pojo, jGen, provider);
          }
        } finally {
          serializationMode = false;
        }
      }
    } finally {
      propertyPath.removeLast();
      currentNode = parentNode;
    }
  }

  private void processFirstPass(Node parentNode, Object pojo, SerializerProvider provider,
                                PropertyWriter writer, JsonGenerator generator) throws Exception {
    Leaf leaf = Leaf.copyOf(propertyPath);

    // if this property _can_ contribute towards the path leading to a matching leaf then we have to check
    boolean matches = matcher.matches(leaf);
    if (matches || matcher.matchesParent(leaf)) {
      if (parentNode.isRoot()) {
        // make sure we don't actually write anything to the output, only replace if we are the root node, it will
        // be passed to other nodes as needed via recursive calls
        generator = new JsonFactory().createGenerator(CharStreams.nullWriter());
      }

      // prepare a node for this property so child branches can add to it as needed
      currentNode = new Node(parentNode, writer.getName());
      writer.serializeAsField(pojo, generator, provider);

      // check the results of processing the children
      if (currentNode.isEmpty()) {
        // either we don't have any children or none of them are matching leaves.
        // in any case
        if (matches) {
          // it turns out we match anyway so add this node
          parentNode.addChild(currentNode);
        }
      } else {
        // child leafs match so we need to include this node as a parent of them
        parentNode.addChild(currentNode);
      }
    }
  }

  @Override
  protected boolean include(BeanPropertyWriter writer) {
    return include((PropertyWriter) writer);
  }

  @Override
  protected boolean include(PropertyWriter writer) {
    checkNotNull(writer);
    return true; // we have overridden the checking, by the time this is called everything should match
  }

  /**
   * Encapsulates a write request for later processing.
   */
  private static class Node {
    @Nullable
    private final Node parent;
    private final String name;
    private Deque<Node> children;

    private Node() {
      this.name = "<< root >>";
      this.parent = null;
    }

    private Node(Node parent, String name) {
      this.parent = checkNotNull(parent);
      this.name = checkNotNull(name);
    }

    public boolean isRoot() {
      return parent == null;
    }

    public void addChild(Node child) {
      if (children == null) {
        children = Lists.newLinkedList();
      }
      children.add(checkNotNull(child));
    }

    public boolean isEmpty() {
      return children == null || children.isEmpty();
    }

    public boolean matchesName(PropertyWriter writer) {
      return name.equals(writer.getName());
    }

    public Node popChild() {
      return children.removeFirst();
    }

    public boolean matchesFirstChild(PropertyWriter writer) {
      if (children == null) {
        return false;
      }
      Node child = children.peekFirst();
      return child != null && child.matchesName(writer);
    }

    @Override
    public String toString() {
      return name + ' ' + (children == null ? "[]" : children);
    }
  }
}
