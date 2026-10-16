"use client";

import { useEffect, useState } from "react";
import { draftStore, type LocalDraft } from "@/data/draftStore";
import { getPostRepo } from "@/data/postRepo.wire";
import { getTemplateRepo } from "@/data/templateRepo.wire";
import { loadRemote } from "@/data/postSync";
import { bn } from "@/i18n/bn";
import { EMPTY_DOC, docToText, makeExcerpt, parseBody, type DocNode } from "@/lib/editorDoc";
import { formatDate } from "@/lib/datetime";
import { remoteChanged } from "@/lib/syncPlan";
import { builtinFor, parseTplParam } from "@/lib/templates";
import { useSiteTimezone } from "../../../SiteSettingsProvider";
import { EditorSurface, type Loaded } from "./EditorSurface";

export type State = { kind: "loading" } | { kind: "error" } | { kind: "ready"; data: Loaded };

const fromLocal = async (postId: string, l: LocalDraft, pub?: Loaded["pub"]): Promise<State> => {
  const doc = parseBody(l.body);
  return {
    kind: "ready",
    data: {
      title: l.title,
      doc,
      createdAt: l.createdAt,
      pos: await draftStore.loadPos(postId).catch(() => undefined),
      excerpt: l.excerpt,
      excerptManual: l.excerptManual ?? l.excerpt !== makeExcerpt(docToText(doc)),
      pub: pub ?? { status: "draft", hasUnpublished: false },
    },
  };
};

/**
 * ড্রাফট-ক্রম: (১) সংরক্ষণহীন (dirty) লোকাল ড্রাফট — সবচেয়ে নতুন কাজ, sync/conflict engine দেখবে;
 * (২) নইলে রিমোট (drafts/{id} → posts/{id}); রিমোট বদলে থাকলে পুরনো ক্লিন লোকাল কপি বাতিল;
 * (৩) রিমোটে না পৌঁছালে ক্লিন লোকাল কপি; (৪) কিছুই না থাকলে খালি নতুন লেখা
 */
/** নতুন লেখার ছাঁচ (শিরোনাম+doc); না পেলে/ব্যর্থ হলে null ⇒ খালি লেখা (ছাঁচ ছাড়া লেখা আটকায় না) */
async function resolveTemplate(raw: string | null, today: string): Promise<{ title: string; doc: DocNode } | null> {
  const ref = parseTplParam(raw);
  if (!ref) return null;
  if (ref.kind === "builtin") {
    const b = builtinFor(ref.id, today);
    return { title: b.title, doc: b.doc };
  }
  try {
    const t = await (await getTemplateRepo()).get(ref.id);
    return t ? { title: "", doc: parseBody(t.body) } : null;
  } catch {
    return null;
  }
}

export async function load(postId: string, isNew: boolean, tpl: string | null = null, today = ""): Promise<State> {
  const local = await draftStore.loadDraft(postId).catch(() => undefined);
  if (local?.dirty) {
    const pub = await getPostRepo()
      .then((r) => loadRemote(r, postId))
      .then((rv) => (rv ? { status: rv.post.status === "published" ? ("published" as const) : ("draft" as const), hasUnpublished: true } : undefined))
      .catch(() => undefined);
    return fromLocal(postId, local, pub);
  }
  try {
    const repo = await getPostRepo();
    const rv = await loadRemote(repo, postId);
    if (rv) {
      const meta = await draftStore.getSync(postId).catch(() => undefined);
      const pub = {
        status: rv.post.status === "published" ? ("published" as const) : ("draft" as const),
        hasUnpublished: !!rv.draft,
      };
      if (local && !remoteChanged(rv.stamp, meta?.baseMs, local.updatedAt)) return fromLocal(postId, local, pub);
      // রিমোটই সত্য: পুরনো ক্লিন লোকাল কপি সরিয়ে base বসানো (প্রথম সম্পাদনায় conflict-যাচাই ঠিক চলবে)
      await draftStore.deleteDraft(postId).catch(() => undefined);
      await draftStore.setSync(postId, { baseMs: rv.stamp, syncedUpdatedAt: 0 }).catch(() => undefined);
      const doc = parseBody(rv.body);
      return {
        kind: "ready",
        data: {
          title: rv.title,
          doc,
          createdAt: rv.post.createdAt.getTime(),
          excerpt: rv.excerpt,
          excerptManual: rv.excerpt !== makeExcerpt(docToText(doc)),
          pub,
        },
      };
    }
  } catch {
    // নেট না পেলে: ক্লিন লোকাল কপি থাকলে তা; পুরনো লেখায় নইলে ভুল করে খালি খোলা নয়; নতুন লেখা অফলাইনেও শুরু করা যায়
    if (local) return fromLocal(postId, local);
    if (!isNew) return { kind: "error" };
  }
  if (local) return fromLocal(postId, local);
  const tp = isNew ? await resolveTemplate(tpl, today) : null;
  return {
    kind: "ready",
    data: {
      title: tp?.title ?? "",
      doc: tp?.doc ?? EMPTY_DOC,
      createdAt: Date.now(),
      excerpt: "",
      excerptManual: false,
      pub: { status: "draft", hasUnpublished: false },
    },
  };
}

export function PostEditor({ postId, isNew, tpl = null, backToSite = null }: { postId: string; isNew: boolean; tpl?: string | null; backToSite?: string | null }) {
  const tz = useSiteTimezone();
  const [state, setState] = useState<State>({ kind: "loading" });
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    let dead = false;
    load(postId, isNew, tpl, formatDate(new Date(), tz)).then((s) => {
      if (!dead) setState(s);
    });
    return () => {
      dead = true;
    };
  }, [postId, isNew, tpl, tz, attempt]);

  if (state.kind === "ready") return <EditorSurface postId={postId} data={state.data} backToSite={backToSite} />;
  if (state.kind === "error") {
    return (
      <p role="alert" className="text-muted">
        {bn.editor.loadFailed}{" "}
        <button
          type="button"
          className="min-h-11 text-accent-text underline"
          onClick={() => {
            setState({ kind: "loading" });
            setAttempt((a) => a + 1);
          }}
        >
          {bn.editor.retry}
        </button>
      </p>
    );
  }
  return (
    <p role="status" aria-live="polite" className="text-muted">
      {bn.editor.loading}
    </p>
  );
}
