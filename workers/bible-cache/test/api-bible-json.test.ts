import assert from "node:assert/strict";
import { describe, it } from "node:test";
import {
  isHeadingParaStyle,
  normalizeDuplicateVerseDigits,
  parseApiBibleChapterContent,
} from "../src/api-bible-json";

describe("parseApiBibleChapterContent", () => {
  it("maps s1 para to heading and verse para to paragraph", () => {
    const content = [
      {
        name: "para",
        attrs: { style: "s1" },
        items: [{ text: "The Beginning", type: "text" }],
      },
      {
        name: "para",
        attrs: { style: "p" },
        items: [
          { attrs: { number: "1", style: "v" }, text: "In the beginning God created the heavens and the earth." },
        ],
      },
    ];

    const blocks = parseApiBibleChapterContent(content);
    assert.equal(blocks.length, 2);
    assert.equal(blocks[0].type, "heading");
    assert.equal(blocks[0].text, "The Beginning");
    assert.equal(blocks[1].type, "paragraph");
    assert.match(blocks[1].text, /^\[1\] In the beginning/);
  });

  it("strips duplicate verse digit after v marker in paragraph text", () => {
    const content = [
      {
        name: "para",
        attrs: { style: "p" },
        items: [
          {
            attrs: { number: "1", style: "v" },
            items: [
              { text: "1", type: "text" },
              { text: "Some time later, Jesus went up to Jerusalem.", type: "text" },
            ],
          },
          {
            attrs: { number: "2", style: "v" },
            text: "2 Now there is in Jerusalem near the Sheep Gate a pool.",
          },
        ],
      },
    ];

    const blocks = parseApiBibleChapterContent(content);
    assert.equal(blocks.length, 1);
    assert.equal(
      blocks[0].text,
      "[1] Some time later, Jesus went up to Jerusalem. [2] Now there is in Jerusalem near the Sheep Gate a pool.",
    );
  });

  it("does not treat short p-style lines as headings", () => {
    const content = [
      {
        name: "para",
        attrs: { style: "p" },
        items: [{ text: "He said hello.", type: "text" }],
      },
      {
        name: "para",
        attrs: { style: "p" },
        items: [{ text: "She replied.", type: "text" }],
      },
    ];
    const blocks = parseApiBibleChapterContent(content);
    assert.equal(blocks.length, 2);
    assert.equal(blocks[0].type, "paragraph");
    assert.equal(blocks[1].type, "paragraph");
  });
});

describe("normalizeDuplicateVerseDigits", () => {
  it("cleans bracket paragraphs from legacy cache strings", () => {
    const raw =
      "[1] 1 First. [4] 4 [5] 5 Fifth.";
    assert.equal(
      normalizeDuplicateVerseDigits(raw),
      "[1] First. [4] [5] Fifth.",
    );
  });
});

describe("isHeadingParaStyle", () => {
  it("recognizes section styles", () => {
    assert.equal(isHeadingParaStyle("s1"), true);
    assert.equal(isHeadingParaStyle("s2"), true);
    assert.equal(isHeadingParaStyle("ms"), true);
    assert.equal(isHeadingParaStyle("p"), false);
  });
});
