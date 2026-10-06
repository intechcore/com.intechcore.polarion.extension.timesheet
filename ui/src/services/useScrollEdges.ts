import { useLayoutEffect, useRef, useState } from 'react';

export interface ScrollEdges {
  /** Days are scrolled under the WorkItem column. */
  left: boolean;
  /** More days wait to the right. */
  right: boolean;
}

// Tells which side of a horizontal scroller hides content, so the report can shade that side.
// A ResizeObserver catches the cases without a scroll event: the first layout, a resized widget
// and a table that grows.
export default function useScrollEdges<T extends HTMLElement>() {
  const ref = useRef<T>(null);
  const [edges, setEdges] = useState<ScrollEdges>({ left: false, right: false });

  useLayoutEffect(() => {
    const el = ref.current!;
    const update = () => {
      const left = el.scrollLeft > 0;
      // 1px of slack: a fractional width can leave scrollLeft just short of the end.
      const right = el.scrollLeft + el.clientWidth < el.scrollWidth - 1;
      setEdges((prev) => (prev.left === left && prev.right === right ? prev : { left, right }));
    };
    update();

    el.addEventListener('scroll', update, { passive: true });
    const observer = new ResizeObserver(update);
    observer.observe(el);
    [...el.children].forEach((child) => observer.observe(child));
    return () => {
      el.removeEventListener('scroll', update);
      observer.disconnect();
    };
  }, []);

  return { ref, edges };
}
