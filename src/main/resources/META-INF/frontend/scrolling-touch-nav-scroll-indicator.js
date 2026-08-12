// Manages chevron visibility for .scrolling-touch-nav elements via CSS state classes.
// Java owns scrolling-touch-nav-has-overflow (structure); this module owns
// scrolling-touch-nav-scrolled and scrolling-touch-nav-at-end (scroll position). CSS translates
// those classes into chevron display values.

function initScrollingTouchNav(bar) {
    const wrapper = bar.closest('.scrolling-touch-nav-wrapper');
    if (!wrapper) return;

    // The slot element (wrapper's parent) defaults to touch-action:auto;
    // set it explicitly so the ancestor chain doesn't offer a y-pan route.
    if (wrapper.parentElement) {
        wrapper.parentElement.style.touchAction = 'pan-x';
    }

    function updateChevrons() {
        const overflows = bar.scrollWidth > bar.clientWidth + 1;
        const scrolled = bar.scrollLeft > 0;
        const atEnd = !overflows || bar.scrollLeft >= bar.scrollWidth - bar.clientWidth - 1;
        wrapper.classList.toggle('scrolling-touch-nav-scrolled', scrolled);
        wrapper.classList.toggle('scrolling-touch-nav-at-end', atEnd);
    }

    bar.addEventListener('scroll', updateChevrons, { passive: true });

    // Re-evaluate after Java rebuilds bar children on window resize.
    new MutationObserver(() => requestAnimationFrame(updateChevrons))
        .observe(bar, { childList: true });

    // Evaluate initial state once the browser has completed layout.
    requestAnimationFrame(updateChevrons);

    // Chevron taps: scroll exactly one page left or right, landing on a snap point.
    const scrollPage = (dir) => {
        const count = bar.children.length;
        if (count === 0) return;
        const itemWidth = bar.scrollWidth / count;
        const itemsPerPage = Math.max(1, Math.round(bar.clientWidth / itemWidth));
        const pageWidth = itemsPerPage * itemWidth;
        const currentPage = Math.round(bar.scrollLeft / pageWidth);
        const targetPage = Math.max(0, currentPage + dir);
        bar.scrollTo({ left: targetPage * pageWidth, behavior: 'smooth' });
    };

    const leftChevron = wrapper.querySelector('.scrolling-touch-nav-chevron-left');
    const rightChevron = wrapper.querySelector('.scrolling-touch-nav-chevron-right');
    if (leftChevron) leftChevron.addEventListener('click', () => scrollPage(-1));
    if (rightChevron) rightChevron.addEventListener('click', () => scrollPage(1));
}

const scrollingTouchNavObserver = new MutationObserver(mutations => {
    for (const mutation of mutations) {
        for (const node of mutation.addedNodes) {
            if (node.nodeType !== 1) continue;
            if (node.classList?.contains('scrolling-touch-nav')) {
                initScrollingTouchNav(node);
            } else {
                node.querySelectorAll?.('.scrolling-touch-nav').forEach(initScrollingTouchNav);
            }
        }
    }
});

scrollingTouchNavObserver.observe(document.body, { childList: true, subtree: true });
