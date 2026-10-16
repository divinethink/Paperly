import { notFound } from "next/navigation";
import { isValidPostId } from "@/lib/id";
import { PostEditor } from "./PostEditor";

/** ফেরার পথ শুধু সাইটের নিজের `/blog/<slug>` (open-redirect নয়); নইলে null */
function safeBackPath(v: string | undefined): string | null {
  return typeof v === "string" && v.length <= 300 && /^\/blog\/[^\s?#\\/]+$/.test(v) ? v : null;
}

export default async function EditPostPage({
  params,
  searchParams,
}: {
  params: Promise<{ postId: string }>;
  searchParams: Promise<{ new?: string; tpl?: string; back?: string }>;
}) {
  const { postId } = await params;
  const { new: isNew, tpl, back } = await searchParams;
  if (!isValidPostId(postId)) notFound();
  return <PostEditor postId={postId} isNew={isNew === "1"} tpl={isNew === "1" ? (tpl ?? null) : null} backToSite={safeBackPath(back)} />;
}
