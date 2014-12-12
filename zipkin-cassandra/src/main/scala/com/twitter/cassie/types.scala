package com.twitter.cassie

import types._

/**
 * Implicit conversions for all of Cassie's special types.
 */
package object types {
  implicit def String2LexicalUUID(s: String): LexicalUUID = LexicalUUID(s)
  implicit def LexicalUUID2String(uuid: LexicalUUID): String = uuid.toString
}
