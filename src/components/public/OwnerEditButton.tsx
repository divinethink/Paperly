"use client";
import { useSyncExternalStore } from "react";
import { bn } from "@/i18n/bn";
import { hasOwnerHint } from "@/lib/ownerHint";

const subscribe = () => () => undefined;

/** শুধু owner-এর ব্রাউজারে (লগইনের চিহ্ন থাকলে, ফুটারের ড্যাশবোর্ড লিংকের একই ব্যবস্থা) ছোট সম্পাদনা-আইকন।
 * চিহ্নটা UI-ইঙ্গিত মাত্র; আসল সুরক্ষা middleware ও Firestore rules-এ। static HTML-এ কিছু থাকে না।
 * কেন সাধারণ <a>: next/link-এর client-navigation-এ ব্রাউজার পাবলিক পেজের CSP (connect-src 'self') রেখে দেয়,
 * ফলে dashboard-এ Firestore আটকে প্রথমবার 'লেখা খোলা যায়নি' আসে। পুরো-পেজ লোডে dashboard-এর নিজের CSP বসে।
 * `back` = সম্পাদনা শেষে ← বাটন যে পাবলিক পথে ফিরবে (শুধু /blog/… — editor যাচাই করে)। */
export function OwnerEditButton({ postId, backPath }: { postId: string; backPath: string }) {
  const on = useSyncExternalStore(subscribe, hasOwnerHint, () => false);
  if (!on) return null;
  return (
    <a
      href={`/dashboard/posts/${postId}/edit?back=${encodeURIComponent(backPath)}`}
      aria-label={bn.public.ownerEditPost}
      title={bn.public.ownerEditPost}
      className="-my-2 -me-2 inline-flex size-11 items-center justify-center text-muted hover:text-accent-text"
    >
      <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
        <path d="M12 20h9" />
        <path d="M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4Z" />
      </svg>
    </a>
  );
}
