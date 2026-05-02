package com.example.lrucache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import org.junit.jupiter.api.Test;

final class DoublyLinkedHashMapTest {
  @Test
  void putNewKeyReturnsNullAndIsHead() {
    DoublyLinkedHashMap<String, Integer> map = new DoublyLinkedHashMap<>();

    assertNull(map.put("a", 1));

    DoublyLinkedHashMap.Node<String, Integer> head = map.peekFirst();
    assertNotNull(head);
    assertEquals("a", head.key);
  }

  @Test
  void putExistingKeyReturnsOldAndMovesToHead() {
    DoublyLinkedHashMap<String, String> map = new DoublyLinkedHashMap<>();

    assertNull(map.put("key", "v1"));
    assertNull(map.put("other", "v3"));

    assertEquals("v1", map.put("key", "v2"));
    assertEquals("key", map.peekFirst().key);
    assertEquals("other", map.peekLast().key);
  }

  @Test
  void getMissingReturnsNull() {
    DoublyLinkedHashMap<String, Integer> map = new DoublyLinkedHashMap<>();

    assertNull(map.get("missing"));
  }

  @Test
  void getExistingReturnsValueDoesNotMoveToFront() {
    DoublyLinkedHashMap<String, Integer> map = new DoublyLinkedHashMap<>();

    map.put("a", 1);
    map.put("b", 2);

    assertEquals("b", map.peekFirst().key);
    assertEquals("a", map.peekLast().key);

    assertEquals(1, map.get("a"));

    assertEquals("b", map.peekFirst().key);
    assertEquals("a", map.peekLast().key);
  }

  @Test
  void moveToFrontMakesNodeHead() {
    DoublyLinkedHashMap<String, Integer> map = new DoublyLinkedHashMap<>();

    map.put("a", 1);
    map.put("b", 2);
    map.put("c", 3);

    DoublyLinkedHashMap.Node<String, Integer> last = map.peekLast();
    assertNotNull(last);
    assertEquals("a", last.key);

    map.moveToFront(last);
    assertEquals("a", map.peekFirst().key);
  }

  @Test
  void removeExistingReturnsValueAndUnlinksNode() {
    DoublyLinkedHashMap<String, Integer> map = new DoublyLinkedHashMap<>();

    map.put("a", 1);
    map.put("b", 2);

    assertEquals(1, map.remove("a"));
    assertFalse(map.containsKey("a"));
    assertEquals(1, map.size());
    assertEquals("b", map.peekFirst().key);
    assertEquals("b", map.peekLast().key);
  }

  @Test
  void removeLastOnEmptyIsNoOp() {
    DoublyLinkedHashMap<String, Integer> map = new DoublyLinkedHashMap<>();

    map.removeLast();

    assertEquals(0, map.size());
    assertNull(map.peekFirst());
    assertNull(map.peekLast());
  }

  @Test
  void removeLastRemovesTailAndPeekLastNullAfter() {
    DoublyLinkedHashMap<String, Integer> map = new DoublyLinkedHashMap<>();

    map.put("a", 1);

    DoublyLinkedHashMap.Node<String, Integer> last = map.peekLast();
    assertNotNull(last);
    assertEquals("a", last.key);

    map.removeLast();
    assertFalse(map.containsKey("a"));
    assertNull(map.peekLast());
  }

  @Test
  void clearResetsState() {
    DoublyLinkedHashMap<String, Integer> map = new DoublyLinkedHashMap<>();

    map.put("a", 1);
    map.put("b", 2);

    map.clear();

    assertEquals(0, map.size());
    assertNull(map.peekLast());
    assertNull(map.peekFirst());
  }

  @Test
  void orderAfterMultiplePutsMatchesHeadAndTail() {
    DoublyLinkedHashMap<String, Integer> map = new DoublyLinkedHashMap<>();

    map.put("a", 1);
    map.put("b", 2);
    map.put("c", 3);

    assertEquals("c", map.peekFirst().key);
    assertEquals("a", map.peekLast().key);
  }
}
