import type { ReactNode } from "react";
import Link from "next/link";
import { Cover } from "@/components/public/Cover";
import { Gallery } from "@/components/public/Gallery";
import { PostBody } from "@/components/public/PostBody";
import { Toc } from "@/components/public/Toc";
import type { TocItem } from "@/lib/toc";
import { bn } from "@/i18n/bn";
import type { CardLink, CoverView } from "@/lib/cards";

/** লেখার পাতা: পাবলিক `/blog/[slug]` ও owner-প্রিভিউ দুই জায়গায় একই (শিরোনাম, meta, chip, allowlist-renderer) — Arch §১৭ P4a */
export function PostArticle({
  title,
  meta,
  links = [],
  body,
  cover,
  gallery = [],
  toc = [],
  anchors = false,
  poem = false,
  after,
}: {
  title: string;
  meta: ReactNode;
  links?: readonly CardLink[];
  body: string;
  /** পাবলিক পেজে লেখার cover (প্রিভিউ-তে নেই) */
  cover?: CoverView;
  /** P8a-২: লেখার ছবি-গ্যালারি (লেখকের ক্রমে; খালি হলে কিছু দেখায় না) */
  gallery?: readonly CoverView[];
  /** P10b-১: স্বয়ংক্রিয় সূচি (খালি = নেই) ও heading-anchor (পাবলিক পেজে) */
  toc?: readonly TocItem[];
  anchors?: boolean;
  /** P10b-২: কবিতা (contentType=poetry) — কেন্দ্রে, পঙ্‌ক্তি-ভাঙা অক্ষত */
  poem?: boolean;
  /** লেখার শেষ-সারির ডানে (owner-এর সম্পাদনা-আইকন; পাঠকের জন্য খালি) */
  after?: ReactNode;
}) {
  return (
    <article id="post-article" className={poem ? "text-center" : undefined}>
      <h1 className="font-serif text-3xl font-bold leading-snug">{title || bn.library.untitled}</h1>
      <p className="mt-2 text-sm text-muted">{meta}</p>
      {cover && (
        <figure className="mt-4 overflow-hidden rounded-2xl bg-line">
          <Cover cover={cover} mode="natural" priority sizes="(min-width: 768px) 720px, 100vw" />
        </figure>
      )}
      <Toc items={toc} />
      <div className={`reading mt-6${poem ? " reading-poem" : ""}`}>
        <PostBody body={body} anchors={anchors} />
      </div>
      {gallery.length > 0 && <Gallery items={gallery} />}
      {(links[0] || after) && (
        <div className={`mt-3 items-center gap-3 text-start ${poem ? "grid grid-cols-[1fr_auto_1fr]" : "flex justify-between"}`}>
          {poem && <span aria-hidden="true" />}
          <span>
            {links[0] &&
              (links[0].href ? (
                <Link href={links[0].href} prefetch={false} className="inline-flex min-h-8 items-center rounded-full border border-line bg-surface px-3 text-sm text-accent-text">
                  {links[0].name}
                </Link>
              ) : (
                <span className="inline-flex min-h-8 items-center rounded-full border border-line bg-surface px-3 text-sm text-muted">{links[0].name}</span>
              ))}
          </span>
          <span className="justify-self-end">{after}</span>
        </div>
      )}
    </article>
  );
}
