import { useState, type ReactNode } from 'react';
import { useCursor } from '@react-three/drei';
import type { ThreeEvent } from '@react-three/fiber';
import type { SceneObjectBinding } from '../../../../apis/games/objects/SceneObjectBinding';
import type { SceneNodeId } from '../../../../objects/SceneNodeId';

type Props = { node: SceneNodeId; objects: readonly SceneObjectBinding[]; children: ReactNode;
  onPick: (object: SceneObjectBinding, event: ThreeEvent<MouseEvent>) => void };
// Every rendered investigation item goes through its authored registration, including observation-only items.
export default function SceneItem({ node, objects, children, onPick }: Props) {
  const [hovered, setHovered] = useState(false);
  useCursor(hovered);
  const object = objects.find(o => o.node === node);
  if (!object) throw new Error(`Unregistered scene item: ${node}`);
  return <group onClick={event => { event.stopPropagation(); if (event.delta <= 5) onPick(object, event); }}
    onPointerOver={event => { event.stopPropagation(); setHovered(true); }} onPointerOut={() => setHovered(false)}>
    {children}
  </group>;
}
