'use client';

import { useEffect, useState } from 'react';
import { api } from '@/lib/api';
import { getToken, Role, ROLE_LABEL } from '@/lib/auth';

type Node = {
  id: string;
  name: string;
  role: Role;
  grade: string | null;
  managerId: string | null;
  children?: Node[];
};

const ROLE_COLOR: Record<Role, string> = {
  ADMIN: 'bg-purple-100 text-purple-700',
  SENIOR_MANAGER: 'bg-indigo-100 text-indigo-700',
  REGIONAL_MANAGER: 'bg-blue-100 text-blue-700',
  FIELD_PERSON: 'bg-slate-100 text-slate-700',
};

function buildTree(users: Node[]): Node[] {
  const byId: Record<string, Node> = {};
  users.forEach((u) => (byId[u.id] = { ...u, children: [] }));
  const roots: Node[] = [];
  users.forEach((u) => {
    if (u.managerId && byId[u.managerId]) {
      byId[u.managerId].children!.push(byId[u.id]);
    } else {
      roots.push(byId[u.id]);
    }
  });
  return roots;
}

function TreeNode({ node, depth = 0 }: { node: Node; depth?: number }) {
  return (
    <div className="border-l border-slate-200 pl-4 ml-2">
      <div className="flex items-center gap-2 py-1.5">
        <span className={`text-xs px-2 py-0.5 rounded ${ROLE_COLOR[node.role]}`}>
          {ROLE_LABEL[node.role]}
        </span>
        <span className="font-medium">{node.name}</span>
        {node.grade && <span className="text-xs text-slate-500">({node.grade})</span>}
      </div>
      {node.children?.map((c) => <TreeNode key={c.id} node={c} depth={depth + 1} />)}
    </div>
  );
}

export default function HierarchyPage() {
  const [tree, setTree] = useState<Node[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    api<{ users: Node[] }>('/api/users/hierarchy', { token: getToken() })
      .then((r) => setTree(buildTree(r.users)))
      .finally(() => setLoading(false));
  }, []);

  return (
    <div className="space-y-5">
      <h1 className="text-2xl font-semibold">Hierarchy</h1>
      <div className="bg-white border border-slate-200 rounded-lg p-5">
        {loading ? (
          <div className="text-slate-500">Loading…</div>
        ) : tree.length === 0 ? (
          <div className="text-slate-500">No active users</div>
        ) : (
          tree.map((root) => <TreeNode key={root.id} node={root} />)
        )}
      </div>
    </div>
  );
}
