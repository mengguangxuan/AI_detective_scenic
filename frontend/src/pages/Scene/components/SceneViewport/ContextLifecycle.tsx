import { useEffect } from 'react';
import { useThree } from '@react-three/fiber';

// R3F deliberately loses an old renderer's context after unmount. It is not a device failure.
export default function ContextLifecycle({ onLost }: { onLost: (lost: boolean) => void }) {
  const gl = useThree(state => state.gl);
  const invalidate = useThree(state => state.invalidate);
  useEffect(() => {
    const canvas = gl.domElement;
    let active = true;
    function lost(event: Event) {
      if (!active || !canvas.isConnected) return;
      event.preventDefault(); // Keep this canvas mounted so the browser can restore its context.
      onLost(true);
    }
    function restored() {
      if (!active || !canvas.isConnected) return;
      onLost(false); invalidate();
    }
    canvas.addEventListener('webglcontextlost', lost);
    canvas.addEventListener('webglcontextrestored', restored);
    onLost(gl.getContext().isContextLost());
    return () => {
      active = false;
      canvas.removeEventListener('webglcontextlost', lost);
      canvas.removeEventListener('webglcontextrestored', restored);
    };
  }, [gl, invalidate, onLost]);
  return null;
}
