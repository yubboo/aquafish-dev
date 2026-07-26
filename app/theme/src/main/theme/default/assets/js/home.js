/*
 * 默认主题首页的轻量搜索交互。
 *
 * 只在浏览器中筛选当前首页已经渲染的卡片，不发起额外请求，也不保存用户输入。
 * 这样即使搜索后端尚未启用，首页的主搜索框仍然具有清楚、可验证的行为。
 */
(() => {
  const form = document.getElementById("af-home-search");
  const input = document.getElementById("af-home-search-input");
  const status = document.getElementById("af-home-search-status");
  const section = document.getElementById("latest-content");

  if (!(form instanceof HTMLFormElement)
      || !(input instanceof HTMLInputElement)
      || !(status instanceof HTMLElement)
      || !(section instanceof HTMLElement)) {
    return;
  }

  const cards = Array.from(
    section.querySelectorAll(".af-home-content-card[data-search]")
  );

  form.addEventListener("submit", (event) => {
    event.preventDefault();

    const keyword = input.value.trim().toLocaleLowerCase("zh-CN");
    let visibleCount = 0;

    cards.forEach((card) => {
      const searchableText = (card.getAttribute("data-search") || "")
        .toLocaleLowerCase("zh-CN");
      const matched = keyword.length === 0 || searchableText.includes(keyword);
      card.hidden = !matched;
      if (matched) {
        visibleCount += 1;
      }
    });

    if (keyword.length === 0) {
      status.textContent = "";
    } else if (visibleCount > 0) {
      status.textContent = `找到 ${visibleCount} 条与“${input.value.trim()}”相关的首页内容。`;
    } else {
      status.textContent = `首页暂时没有与“${input.value.trim()}”匹配的内容。`;
    }

    section.scrollIntoView({ behavior: "smooth", block: "start" });
  });

  input.addEventListener("search", () => {
    if (input.value.length === 0) {
      cards.forEach((card) => {
        card.hidden = false;
      });
      status.textContent = "";
    }
  });
})();
