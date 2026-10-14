import { test, expect, Page } from '@playwright/test';

const prefix = 'work-journey.online.v1.';
async function login(page: Page, email = 'brasil@example.invalid', waitForEntry = true) {
  const password = process.env['EVALUATION_PASSWORD'];
  if (!password) throw new Error('Evaluation password is required outside Git');
  await page.goto('/');
  await page.getByLabel('Email', { exact: true }).fill(email);
  await page.getByLabel('Senha', { exact: true }).fill(password);
  await page.getByRole('button', { name: 'Entrar', exact: true }).click();
  if (waitForEntry) await expect(page.getByRole('button', { name: 'Registrar entrada', exact: true })).toBeEnabled();
  else await expect(page.getByRole('heading', { name: 'Olá, Colaborador Brasil' })).toBeVisible();
}
async function logout(page: Page) {
  await page.getByRole('button', { name: 'Sair', exact: true }).click();
  // Do not navigate away while CSRF/logout is still in flight.
  await expect(page.getByRole('heading', { name: 'Entrar', exact: true })).toBeVisible();
  expect((await page.request.get('/api/me')).status()).toBe(401);
}

async function diary(page: Page) {
  return page.evaluate(prefix => Object.keys(localStorage).filter(key => key.startsWith(prefix))
    .map(key => JSON.parse(localStorage.getItem(key)!)), prefix);
}
async function current(page: Page) { return (await page.request.get('/api/me/work-sessions/current')).json(); }

// The transport loses the response after route.fetch runs the real Spring/SQL transaction.
test('real journey, lost responses, explicit retry, keyboard and responsive reflow', async ({ page }, testInfo) => {
  await login(page);
  await expect(page.locator('#access-heading')).toBeFocused();
  let accepted: { requestKey: string; eventId: string; occurredAtUtc: string } | undefined;
  let posts = 0;
  page.on('request', request => { if (request.url().endsWith('/api/me/work-events') && request.method() === 'POST') posts++; });
  await page.route('**/api/me/work-events', async route => {
    const response = await route.fetch();
    expect(response.status()).toBe(201);
    accepted = await response.json();
    await route.abort('failed');
  }, { times: 1 });
  await page.getByRole('button', { name: 'Registrar entrada', exact: true }).focus();
  await page.keyboard.press('Enter');
  await expect(page.getByTestId('journey-feedback')).toContainText('tentativa foi preservada');
  await expect(page.locator('#journey-heading')).toBeFocused();
  const saved = await diary(page);
  expect(saved).toHaveLength(1);
  expect(Object.keys(saved[0]).sort()).toEqual(['employeeId', 'expectedWorkSessionId', 'requestKey', 'type', 'userId', 'version']);
  expect(saved[0].requestKey).toBe(accepted!.requestKey);
  const lookup = await page.request.get('/api/me/work-event-requests/' + saved[0].requestKey);
  expect(lookup.status()).toBe(200);
  expect((await lookup.json()).eventId).toBe(accepted!.eventId);
  await page.reload();
  await expect(page.getByRole('button', { name: 'Iniciar pausa', exact: true })).toBeEnabled();
  expect(await diary(page)).toHaveLength(0);
  expect(posts).toBe(1);
  const snapshot = await current(page);
  expect(snapshot.session.lastEvent.id).toBe(accepted!.eventId);
  expect(snapshot.session.lastEvent.occurredAtUtc).toBe(accepted!.occurredAtUtc);
  const peer = await page.context().newPage();
  let peerPosts = 0;
  peer.on('request', request => { if (request.method() === 'POST' && request.url().endsWith('/api/me/work-events')) peerPosts++; });
  await peer.goto('/');
  await expect(peer.getByRole('button', { name: 'Iniciar pausa', exact: true })).toBeEnabled();
  await expect(peer.locator('#access-heading')).toBeFocused();
  await page.getByRole('button', { name: 'Iniciar pausa', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Retomar trabalho', exact: true })).toBeEnabled();
  await expect(page.getByText('Em pausa', { exact: true })).toBeVisible();
  await expect(peer.getByRole('button', { name: 'Retomar trabalho', exact: true })).toBeEnabled();
  await expect(peer.locator('#access-heading')).toBeFocused();
  expect(await diary(peer)).toHaveLength(0);
  expect(peerPosts).toBe(0);
  await peer.close();
  await page.getByRole('button', { name: 'Retomar trabalho', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Iniciar pausa', exact: true })).toBeEnabled();
  await page.getByRole('button', { name: 'Iniciar pausa', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Retomar trabalho', exact: true })).toBeEnabled();
  expect((await current(page)).session.referenceTimeZoneId).toBe('America/Sao_Paulo');
  for (const width of [320, 768, 1280]) {
    await page.setViewportSize({ width, height: 900 });
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
    const box = await page.getByRole('button', { name: 'Encerrar jornada', exact: true }).boundingBox();
    expect(box?.height).toBeGreaterThanOrEqual(44);
    await page.screenshot({ path: testInfo.outputPath(`journey-paused-${width}.png`), fullPage: true });
  }
  await page.evaluate(() => { document.documentElement.style.fontSize = '200%'; });
  await page.setViewportSize({ width: 320, height: 900 });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  await page.evaluate(() => { document.documentElement.style.fontSize = ''; });
  await page.getByRole('button', { name: 'Encerrar jornada', exact: true }).focus();
  await page.keyboard.press('Enter');
  await expect(page.getByRole('button', { name: 'Registrar entrada', exact: true })).toBeEnabled();
  expect((await current(page)).session).toBeNull();
  await expect(page.getByText('Encerrar jornada: confirmado.')).toBeVisible();
  await page.getByRole('button', { name: 'Consultar período', exact: true }).click();
  await expect(page.getByTestId('history-feedback')).toContainText('Consulta concluída');
  const history = await (await page.request.get('/api/me/work-sessions?from=2026-10-01&to=2026-10-31')).json();
  expect(history.summary.pendingCount).toBe(0);
  const latest = history.items[0];
  await page.getByRole('button', { name: /^Ver detalhes da jornada de/ }).first().click();
  await expect(page.getByRole('heading', { name: 'Marcações originais' })).toBeVisible();
  await expect(page.locator('.events li')).toHaveCount(5);
  const detail = await (await page.request.get('/api/me/work-sessions/' + latest.id)).json();
  expect(detail.session.workDuration).toBe(latest.workDuration);
  expect(detail.intervals.at(-1).kind).toBe('PAUSE');
  await expect(page.getByRole('heading', { name: /^Detalhes da jornada de/ })).toBeFocused();
  const dateBefore = detail.session.workDate;
  await page.getByLabel('Fuso de visualização', { exact: true }).selectOption('Europe/Amsterdam');
  await expect(page.locator('.events li').first()).toContainText('GMT+2');
  expect((await (await page.request.get('/api/me/work-sessions/' + latest.id)).json()).session.workDate).toBe(dateBefore);
  for (const width of [320, 1280]) {
    await page.setViewportSize({ width, height: 900 });
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
    await page.screenshot({ path: testInfo.outputPath(`history-detail-${width}.png`), fullPage: true });
  }
  await page.getByRole('button', { name: 'Voltar ao histórico', exact: true }).click();
  await expect(page.locator('#history-heading')).toBeFocused();

  await page.route('**/api/me/work-events', route => route.abort('failed'), { times: 1 });
  await page.getByRole('button', { name: 'Registrar entrada', exact: true }).click();
  await expect(page.getByTestId('journey-feedback')).toContainText('tentativa foi preservada');
  const pending = (await diary(page))[0];
  expect((await page.request.get('/api/me/work-event-requests/' + pending.requestKey)).status()).toBe(404);
  const postCount = posts;
  let pendingLookups = 0;
  page.on('request', request => { if (request.url().endsWith('/api/me/work-event-requests/' + pending.requestKey)) pendingLookups++; });
  await logout(page);
  await login(page, 'europa@example.invalid');
  expect(await diary(page)).toHaveLength(1);
  expect(pendingLookups).toBe(0);
  await expect(page.getByRole('heading', { name: 'Marcação aguardando confirmação' })).toHaveCount(0);
  await logout(page);
  await login(page, 'brasil@example.invalid', false);
  await expect(page.getByRole('heading', { name: 'Marcação aguardando confirmação' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Consultar resultados', exact: true })).toBeEnabled();
  await page.reload();
  await expect(page.getByRole('heading', { name: 'Marcação aguardando confirmação' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Consultar resultados', exact: true })).toBeEnabled();
  expect(posts).toBe(postCount);
  await page.getByRole('button', { name: 'Consultar e repetir esta tentativa', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Iniciar pausa', exact: true })).toBeEnabled();
  const repeated = await (await page.request.get('/api/me/work-event-requests/' + pending.requestKey)).json();
  expect(repeated.requestKey).toBe(pending.requestKey);
  expect(repeated.httpStatus).toBe(201);
  expect(await diary(page)).toHaveLength(0);
  await page.getByRole('button', { name: 'Encerrar jornada', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Registrar entrada', exact: true })).toBeEnabled();
  await logout(page);
  await expect(page.getByRole('heading', { name: 'Entrar', exact: true })).toBeVisible();
  await expect(page.locator('#access-heading')).toBeFocused();
});
