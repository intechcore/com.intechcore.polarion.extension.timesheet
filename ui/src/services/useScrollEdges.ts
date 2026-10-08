import { useLayoutEffect, useRef, useState } from 'react';

export interface ScrollEdges {
  /** Days are scrolled under the WorkItem column. */
  left: boolean;
  /** More days wait to the right. */
  right: boolean;
}

export interface ScrollBar {
  /** Where the bar starts: the right edge of the WorkItem column. */
  offset: number;
  /** The scrollable width under the bar: every day column. */
  contentWidth: number;
}

/** The narrowest day: "dd.MM" in the bold 13px of the head. The widest: the width a day had before. */
export const MIN_DAY_WIDTH = 38;
export const MAX_DAY_WIDTH = 48;
// The 5px padding on either side of a day and its 1px right border.
const DAY_CELL_EXTRA = 11;

/**
 * The width of a day that lets the longest month of the period fill the scroller, between the narrowest
 * and the widest day. A month that nearly fits then fits, without a scrollbar for a few pixels, and
 * every block of the period gets the same days.
 */
export function dayWidth(available: number, labelWidth: number, longestMonth: number): number {
  const fill = Math.floor((available - labelWidth) / longestMonth) - DAY_CELL_EXTRA;
  return Math.min(MAX_DAY_WIDTH, Math.max(MIN_DAY_WIDTH, fill));
}

// Tells which side of a horizontal scroller hides content, so the report can shade that side, and
// drives a separate scrollbar under the days only. The native scrollbar of the scroller spans the
// WorkItem column too, and nothing can make it start further right.
// A ResizeObserver catches the cases without a scroll event: the first layout, a resized widget
// and a table that grows.
export default function useScrollEdges<T extends HTMLElement, B extends HTMLElement>(longestMonth: number) {
  const ref = useRef<T>(null);
  const barRef = useRef<B>(null);
  const [edges, setEdges] = useState<ScrollEdges>({ left: false, right: false });
  const [bar, setBar] = useState<ScrollBar | null>(null);
  const [day, setDay] = useState(MAX_DAY_WIDTH);

  useLayoutEffect(() => {
    const el = ref.current!;
    const barEl = barRef.current!;
    const update = () => {
      // The scroller holds a report table, which always has its WorkItem head.
      const label = el.querySelector<HTMLElement>('th')!.offsetWidth;
      setDay(dayWidth(el.clientWidth, label, longestMonth));

      const left = el.scrollLeft > 0;
      // 1px of slack: a fractional width can leave scrollLeft just short of the end.
      const right = el.scrollLeft + el.clientWidth < el.scrollWidth - 1;
      setEdges((prev) => (prev.left === left && prev.right === right ? prev : { left, right }));

      const scrollable = el.scrollWidth - el.clientWidth > 1;
      const contentWidth = el.scrollWidth - label;
      setBar((prev) => {
        if (!scrollable) return null;
        return prev?.offset === label && prev.contentWidth === contentWidth ? prev : { offset: label, contentWidth };
      });
      // The bar was hidden, or the table moved while it was: put its thumb where the table is.
      barEl.scrollLeft = el.scrollLeft;
    };
    update();

    // Both directions: the bar moves the table, and a trackpad or Shift+wheel on the table moves the
    // bar. A position already equal is not set again, which is what stops the two from ping-ponging.
    const fromBar = () => {
      if (el.scrollLeft !== barEl.scrollLeft) el.scrollLeft = barEl.scrollLeft;
    };
    el.addEventListener('scroll', update, { passive: true });
    barEl.addEventListener('scroll', fromBar, { passive: true });
    const observer = new ResizeObserver(update);
    observer.observe(el);
    [...el.children].forEach((child) => observer.observe(child));
    return () => {
      el.removeEventListener('scroll', update);
      barEl.removeEventListener('scroll', fromBar);
      observer.disconnect();
    };
  }, [longestMonth]);

  return { ref, barRef, edges, bar, dayWidth: day };
}
