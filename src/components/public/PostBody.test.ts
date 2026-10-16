import { createElement as h } from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";
import type { CoverView } from "@/lib/cards";
import type { DocNode } from "@/lib/editorDoc";
import { DocView, PostBody } from "./PostBody";

const t = (text: string, marks?: DocNode["marks"]): DocNode => ({ type: "text", text, ...(marks ? { marks } : {}) });
const p = (...c: DocNode[]): DocNode => ({ type: "paragraph", content: c });
const view = (...c: DocNode[]) => renderToStaticMarkup(h(DocView, { doc: { type: "doc", content: c } }));

describe("DocView — allowed", () => {
  it("paragraph, heading H2/H3, hardBreak", () => {
    expect(view(p(t("ক"), { type: "hardBreak" }, t("খ")))).toBe("<p>ক<br/>খ</p>");
    expect(view({ type: "heading", attrs: { level: 2 }, content: [t("শি")] })).toBe("<h2>শি</h2>");
    expect(view({ type: "heading", attrs: { level: 3 }, content: [t("শি")] })).toBe("<h3>শি</h3>");
  });
  it("bulletList, orderedList (start), listItem, blockquote, horizontalRule", () => {
    const li = (s: string): DocNode => ({ type: "listItem", content: [p(t(s))] });
    expect(view({ type: "bulletList", content: [li("এক")] })).toBe("<ul><li><p>এক</p></li></ul>");
    expect(view({ type: "orderedList", attrs: { start: 3 }, content: [li("এক")] })).toBe(
      '<ol start="3"><li><p>এক</p></li></ol>',
    );
    expect(view({ type: "blockquote", content: [p(t("উক্তি"))] })).toBe("<blockquote><p>উক্তি</p></blockquote>");
    expect(view({ type: "horizontalRule" })).toBe("<hr/>");
  });
  it("marks: bold, italic, underline, নিরাপদ link", () => {
    expect(view(p(t("ক", [{ type: "bold" }])))).toBe("<p><strong>ক</strong></p>");
    expect(view(p(t("ক", [{ type: "italic" }])))).toBe("<p><em>ক</em></p>");
    expect(view(p(t("ক", [{ type: "underline" }])))).toBe("<p><u>ক</u></p>");
    expect(view(p(t("ক", [{ type: "link", attrs: { href: "https://example.com/a" } }])))).toBe(
      '<p><a href="https://example.com/a" rel="noopener noreferrer">ক</a></p>',
    );
    expect(view(p(t("ক", [{ type: "link", attrs: { href: "mailto:a@b.co" } }])))).toContain('href="mailto:a@b.co"');
  });
});

describe("DocView — blocked", () => {
  it("allowlist-বহির্ভূত node কিছুই render করে না", () => {
    for (const type of ["image", "iframe", "script", "youtube", "codeBlock", "table", "style"]) {
      expect(view({ type, attrs: { src: "https://x.y/z.png" }, content: [t("ভেতরে")] })).toBe("");
    }
  });
  it("অনিরাপদ link শুধু টেক্সট; অজানা mark বাদ", () => {
    for (const href of ["javascript:alert(1)", "data:text/html,x", "//evil.com", "ftp://x.y"]) {
      expect(view(p(t("ক", [{ type: "link", attrs: { href } }])))).toBe("<p>ক</p>");
    }
    expect(view(p(t("ক", [{ type: "link" }])))).toBe("<p>ক</p>");
    expect(view(p(t("ক", [{ type: "highlight" }])))).toBe("<p>ক</p>");
  });
  it("H1/অবৈধ level → H2; টেক্সটের HTML escape হয়", () => {
    expect(view({ type: "heading", attrs: { level: 1 }, content: [t("শি")] })).toBe("<h2>শি</h2>");
    expect(view(p(t("<script>alert(1)</script>")))).toBe("<p>&lt;script&gt;alert(1)&lt;/script&gt;</p>");
  });
  it("doc নয় এমন root কিছু render করে না", () => {
    expect(renderToStaticMarkup(h(DocView, { doc: p(t("ক")) }))).toBe("");
  });
});

describe("PostBody (sanitize + render)", () => {
  it("বিকৃত body-তে খালি-অনুচ্ছেদ, ক্র্যাশ নয়", () => {
    expect(renderToStaticMarkup(h(PostBody, { body: "{bad" }))).toBe("<p></p>");
  });
  it("body-র ভেতরের script/image node পৌঁছায় না", () => {
    const body = JSON.stringify({
      type: "doc",
      content: [{ type: "script", content: [t("x")] }, { type: "image", attrs: { src: "https://x.y/a.png" } }, p(t("ঠিক"))],
    });
    expect(renderToStaticMarkup(h(PostBody, { body }))).toBe("<p>ঠিক</p>");
  });
});

describe("DocView — anchors", () => {
  const hd = (l: number, s: string): DocNode => ({ type: "heading", attrs: { level: l }, content: [t(s)] });
  it("anchors চালু হলে heading-এ ক্রমিক id (nested-সহ); বন্ধ হলে id নেই", () => {
    const doc: DocNode = { type: "doc", content: [hd(2, "ক"), { type: "blockquote", content: [hd(3, "খ")] }, hd(2, "গ")] };
    expect(renderToStaticMarkup(h(DocView, { doc, anchors: true }))).toBe('<h2 id="h-1">ক</h2><blockquote><h3 id="h-2">খ</h3></blockquote><h2 id="h-3">গ</h2>');
    expect(renderToStaticMarkup(h(DocView, { doc }))).toBe("<h2>ক</h2><blockquote><h3>খ</h3></blockquote><h2>গ</h2>");
  });
});

describe("mediaImage render", () => {
  const cv: CoverView = { cloud: "democloud", publicId: "blog/abc", w: 1600, h: 900, alt: "মিডিয়া-alt" };
  const media = new Map([["m1", cv]]);
  const img = (attrs: Record<string, unknown>): DocNode => ({ type: "mediaImage", attrs });
  const doc = (...c: DocNode[]): DocNode => ({ type: "doc", content: c });
  it("figure>img: cldUrl, srcset, lazy, মাপ; কোনো raw URL পাথ নয়", () => {
    const html = renderToStaticMarkup(h(DocView, { doc: doc(img({ mediaId: "m1", alt: "নিজের alt", caption: "ক্যাপশন <b>" })), media }));
    expect(html).toContain('<figure class="reading-figure">');
    expect(html).toContain("https://res.cloudinary.com/democloud/image/upload/f_auto,q_auto,w_800,c_limit/blog/abc");
    expect(html).toContain('alt="নিজের alt"');
    expect(html).toContain('loading="lazy"');
    expect(html).toContain('width="1600"');
    expect(html).toContain("<figcaption>ক্যাপশন &lt;b&gt;</figcaption>");
  });
  it("doc-alt খালি হলে media-র alt; ক্যাপশন না থাকলে figcaption নেই", () => {
    const html = renderToStaticMarkup(h(DocView, { doc: doc(img({ mediaId: "m1", alt: "" })), media }));
    expect(html).toContain('alt="মিডিয়া-alt"');
    expect(html).not.toContain("figcaption");
  });
  it("media map নেই/id অজানা/অবৈধ cloud ⇒ কিছুই render নয়", () => {
    expect(renderToStaticMarkup(h(DocView, { doc: doc(img({ mediaId: "m1" })) }))).toBe("");
    expect(renderToStaticMarkup(h(DocView, { doc: doc(img({ mediaId: "zz" })), media }))).toBe("");
    const bad = new Map([["m1", { ...cv, cloud: "a/b" }]]);
    expect(renderToStaticMarkup(h(DocView, { doc: doc(img({ mediaId: "m1" })), media: bad }))).toBe("");
  });
  it("PostBody: body-এর raw src পৌঁছায় না; mediaId থেকেই URL", () => {
    const body = JSON.stringify(doc(img({ mediaId: "m1", src: "https://evil.example/x.png" }), { type: "paragraph", content: [t("ঠিক")] }));
    const html = renderToStaticMarkup(h(PostBody, { body, media }));
    expect(html).not.toContain("evil.example");
    expect(html).toContain("res.cloudinary.com/democloud");
    expect(html).toContain("<p>ঠিক</p>");
  });
});

describe("DocView — শেষের ফাঁকা অনুচ্ছেদ বাদ (শুধু দেখানোয়)", () => {
  it("শেষের ফাঁকা/<br>/nbsp অনুচ্ছেদ বাদ; মাঝেরটা থাকে", () => {
    const empty = { type: "paragraph" } as DocNode;
    expect(view(p(t("ক")), empty, p({ type: "hardBreak" }), p(t("\u00a0 ")))).toBe("<p>ক</p>");
    expect(view(p(t("ক")), empty, p(t("খ")), empty)).toBe("<p>ক</p><p></p><p>খ</p>");
  });
});
