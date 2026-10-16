"use client";
import { useSyncExternalStore } from "react";
import { bn } from "@/i18n/bn";
import { hasOwnerHint } from "@/lib/ownerHint";

const subscribe = () => () => undefined;

/** শুধু owner-এর ব্রাউজারে (লগইনের চিহ্ন থাকলে) "ড্যাশবোর্ড" লিংক; অন্য পাঠকের জন্য কিছুই আঁকে না */
export function OwnerLink({ className }: { className: string }) {
  // server/hydration = false (static HTML-এ কিছু নেই); client-এ চিহ্ন থাকলে true
  const on = useSyncExternalStore(subscribe, hasOwnerHint, () => false);
  return on ? (
    // সাধারণ <a>: পুরো-পেজ লোডে dashboard-এর CSP বসে (OwnerEditButton-এর মন্তব্য দেখুন)
    // eslint-disable-next-line @next/next/no-html-link-for-pages -- ইচ্ছাকৃত: পুরো-পেজ লোড
    <a href="/dashboard" className={className}>
      ✎ {bn.public.ownerDashboard}
    </a>
  ) : null;
}
