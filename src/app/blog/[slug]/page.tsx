import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { AuthorBox } from "@/components/public/AuthorBox";
import { SubscribeBox } from "@/components/public/SubscribeBox";
import { PostArticle } from "@/components/public/PostArticle";
import { ReadingChrome } from "@/components/public/ReadingChrome";
import { ReadingResume } from "@/components/public/ReadingResume";
import { SeriesBanner } from "@/components/public/SeriesBanner";
import { PostPager, RelatedPosts } from "@/components/public/PostFooterNav";
import { PublicShell } from "@/components/public/PublicShell";
import { QuoteShare } from "@/components/public/QuoteShare";
import { ReaderBar } from "@/components/public/ReaderTools";
import { OwnerEditButton } from "@/components/public/OwnerEditButton";
import { ViewBeacon } from "@/components/public/ViewBeacon";
import { ShareBar } from "@/components/public/ShareBar";
import { Comments, type CommentView } from "@/components/public/Comments";
import { getPostComments } from "@/data/commentSnapshot";
import { buildThread } from "@/lib/comments";
import { getPublicScopes } from "@/data/publicLists";
import { getSiteUrl } from "@/config/env";
import { hasDefaultOgImage } from "@/data/publicAssets";
import { getPublicMedia, getPublicPages, getPublicSettings, listPublicPosts, listUnlistedPosts } from "@/data/publicSnapshot";
import { bn } from "@/i18n/bn";
import { resolveCover } from "@/lib/cards";
import { resolveGallery } from "@/lib/gallery";
import { cldOgUrl } from "@/lib/cloudinary";
import { formatBnNumber, formatDate } from "@/lib/datetime";
import { pagePath } from "@/lib/pages";
import { blogPath, planSlugMoves, postExcerpt } from "@/lib/publicSite";
import { absoluteUrl } from "@/lib/reader";
import { readingMinutes } from "@/lib/readingTime";
import { adjacentIds, relatedIds, seriesPosition } from "@/lib/related";
import { buildToc } from "@/lib/toc";
import { parseBody } from "@/lib/editorDoc";
import { articleJsonLd, buildSeo, resolvePostSeo } from "@/lib/seo";

// build-সময়ে সব published+public লেখার (এবং বাংলা পুরনো-slug-এর redirect-stub) পেজ; অন্য slug = 404 (Arch §২, §১২)
export const dynamic = "force-static";
export const dynamicParams = false;

type Props = { params: Promise<{ slug: string }> };

// Next বাংলা param কখনো encoded দেয়; slug-এ '%' থাকা অসম্ভব, তাই decode idempotent
const decode = (s: string) => {
  try {
    return decodeURIComponent(s);
  } catch {
    return s;
  }
};

function safeSiteUrl(): string | undefined {
  try {
    return getSiteUrl();
  } catch {
    return undefined; // SITE_URL ছাড়া (লোকাল dev) canonical বাদ
  }
}

async function resolve(rawSlug: string) {
  const slug = decode(rawSlug);
  const posts = await listPublicPosts();
  const post = posts.find((p) => p.slug === slug);
  if (post) return { kind: "post" as const, post, unlisted: false };
  // P11c-১: unlisted = নিজের URL আছে, কোনো তালিকা/sitemap/search-এ নেই (এই পেজেও noindex ও pager/related/series নেই)
  const hidden = (await listUnlistedPosts()).find((p) => p.slug === slug);
  if (hidden) return { kind: "post" as const, post: hidden, unlisted: true };
  const move = planSlugMoves(posts).stubs.find((m) => m.from === slug);
  return move ? { kind: "moved" as const, move } : null;
}

export async function generateStaticParams() {
  const [posts, hidden] = await Promise.all([listPublicPosts(), listUnlistedPosts()]);
  return [...posts.map((p) => ({ slug: p.slug })), ...hidden.map((p) => ({ slug: p.slug })), ...planSlugMoves(posts).stubs.map((m) => ({ slug: m.from }))];
}

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const [r, { siteTitle, allowIndexing }, media] = await Promise.all([resolve((await params).slug), getPublicSettings(), getPublicMedia()]);
  if (!r) return {};
  if (r.kind === "post") {
    const cv = resolveCover(r.post.seo?.ogImageId ?? r.post.coverMediaId, media);
    const o = resolvePostSeo(r.post.seo, { title: r.post.title || bn.library.untitled, description: postExcerpt(r.post), siteName: siteTitle });
    return buildSeo({
      siteUrl: safeSiteUrl(),
      siteName: siteTitle,
      title: o.title,
      description: o.description,
      path: blogPath(r.post.slug),
      allowIndexing,
      noindex: o.noindex || r.unlisted,
      type: "article",
      publishedTime: r.post.publishedAt,
      modifiedTime: r.post.updatedAt,
      hasOgImage: hasDefaultOgImage(),
      ogImage: cv ? (cldOgUrl(cv.cloud, cv.publicId) ?? undefined) : undefined,
    }) as Metadata;
  }
  let canonical: string | undefined;
  try {
    canonical = `${getSiteUrl()}${blogPath(r.move.to)}`;
  } catch {
    canonical = undefined; // SITE_URL ছাড়া (লোকাল dev) canonical বাদ
  }
  return {
    title: `${bn.public.movedTitle} — ${siteTitle}`,
    alternates: canonical ? { canonical } : undefined,
    robots: { index: false, follow: true },
  };
}

export default async function BlogPost({ params }: Props) {
  const [r, { timezone: tz, siteTitle, authorBio, subscribeUrl, subscribeLink }, pages, media] = await Promise.all([resolve((await params).slug), getPublicSettings(), getPublicPages(), getPublicMedia()]);
  if (!r) notFound();

  if (r.kind === "moved") {
    const to = blogPath(r.move.to);
    return (
      <>
        {/* JS ছাড়া redirect (Arch §২); canonical ও noindex metadata-তে */}
        <meta httpEquiv="refresh" content={`0;url=${to}`} />
        <h1 className="font-serif text-2xl font-bold">{bn.public.movedTitle}</h1>
        <p className="mt-2 text-muted">{bn.public.movedBody}</p>
        <Link href={to} prefetch={false} className="mt-4 inline-flex min-h-11 items-center text-accent-text underline">
          {bn.public.movedLink}
        </Link>
      </>
    );
  }

  const { post } = r;
  // category/tag লিংক scope-ডেটার card থেকে (একই build-ডেটা)
  const blogCards = (await getPublicScopes()).find((s) => s.kind === "blog")?.cards ?? [];
  const card = blogCards.find((c) => c.id === post.id);
  // P10a-৩: সম্পর্কিত ও আগের/পরের — একই build-ডেটা (`publishedAt desc`) থেকে, নতুন query নেই
  const posts = await listPublicPosts();
  const byId = new Map(blogCards.map((c) => [c.id, c] as const));
  const adj = r.unlisted ? { older: undefined, newer: undefined } : adjacentIds(posts, post.id);
  const related = (r.unlisted ? [] : relatedIds(posts, post.id)).flatMap((id) => byId.get(id) ?? []);
  // P10b-১: সূচি (heading ≥৩) ও "পর্ব X/Y" — সিরিজ-scope-এর card-তালিকা (seriesOrder-ক্রমে) থেকে
  const toc = buildToc(parseBody(post.body));
  const seriesScope = post.seriesId && !r.unlisted ? (await getPublicScopes()).find((s) => s.kind === "series" && s.term?.id === post.seriesId) : undefined;
  const sp = seriesScope ? seriesPosition(seriesScope.cards, post.id) : null;
  const minutes = readingMinutes(post.wordCount);
  const ldCover = resolveCover(post.seo?.ogImageId ?? post.coverMediaId, media);
  const ld = articleJsonLd({
    siteUrl: safeSiteUrl(),
    siteName: siteTitle,
    headline: post.title || bn.library.untitled,
    description: resolvePostSeo(post.seo, { title: post.title, description: postExcerpt(post), siteName: siteTitle }).description,
    path: blogPath(post.slug),
    image: ldCover ? (cldOgUrl(ldCover.cloud, ldCover.publicId) ?? undefined) : undefined,
    publishedTime: post.publishedAt,
    modifiedTime: post.updatedAt,
  });
  // P11b-২: মন্তব্য শুধু published+public লেখায় (unlisted-এ নয়); তারিখ-লেবেল server-এ (timezone/hydration নিরাপদ)
  const showComments = !r.unlisted;
  const toView = (c: { id: string; authorName: string; content: string; createdAt: Date; byOwner?: boolean }): CommentView => ({
    id: c.id,
    authorName: c.authorName,
    content: c.content,
    dateLabel: formatDate(c.createdAt, tz),
    byOwner: c.byOwner,
  });
  const thread = showComments ? buildThread(await getPostComments(post.id)).map((n) => ({ comment: toView(n.comment), replies: n.replies.map(toView) })) : [];
  const meta = (
    <>
      {post.publishedAt && <time dateTime={post.publishedAt.toISOString()}>{formatDate(post.publishedAt, tz)}</time>}
      {" · "}
      {formatBnNumber(post.wordCount)} {bn.library.words}
      {minutes > 0 && ` · ${bn.public.readMinutes(minutes)}`}
      {post.location && ` · 📍 ${post.location}`}
    </>
  );
  return (
    <PublicShell>
      <ViewBeacon postId={post.id} />
      <ReadingChrome targetId="post-article" title={post.title || bn.library.untitled} />
      <ReadingResume slug={post.slug} targetId="post-article" />
      <script type="application/ld+json" dangerouslySetInnerHTML={{ __html: ld }} />
      <ReaderBar>
        <Link href="/" prefetch={false} className="zen-hide inline-flex min-h-11 items-center text-accent-text">
          ← {bn.public.home}
        </Link>
      </ReaderBar>
      {seriesScope && sp && (
        <div className="zen-hide">
          <SeriesBanner name={seriesScope.term!.name} path={seriesScope.path} index={sp.index} total={sp.total} prev={sp.prev} next={sp.next} />
        </div>
      )}
      <PostArticle toc={toc} anchors poem={post.contentType === "poetry"} title={post.title} meta={meta} links={card?.category ? [card.category] : []} body={post.body} cover={card?.cover} gallery={resolveGallery(post.gallery, media)} after={<OwnerEditButton postId={post.id} backPath={blogPath(post.slug)} />} />
      <div className="zen-hide">
        <ShareBar url={absoluteUrl(safeSiteUrl(), blogPath(post.slug))} />
        <AuthorBox name={siteTitle} bio={authorBio} aboutHref={pages.some((p) => p.slug === "about") ? pagePath("about") : undefined} />
        <SubscribeBox action={subscribeUrl} link={subscribeLink} id="post" variant="post" />
        {showComments && <Comments postId={post.id} thread={thread} open={post.commentsEnabled !== false} tz={tz} />}
        <PostPager older={adj.older ? byId.get(adj.older) : undefined} newer={adj.newer ? byId.get(adj.newer) : undefined} />
        <RelatedPosts cards={related} />
      </div>
      <QuoteShare title={post.title || bn.library.untitled} url={absoluteUrl(safeSiteUrl(), blogPath(post.slug))} />
    </PublicShell>
  );
}
