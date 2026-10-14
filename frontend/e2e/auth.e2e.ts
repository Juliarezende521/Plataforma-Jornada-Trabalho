import { test, expect, Page } from '@playwright/test';

const password = process.env['EVALUATION_PASSWORD'];
test.beforeAll(() => {
  if (!password) throw new Error('EVALUATION_PASSWORD must be configured outside Git');
});

async function login(page: Page, email: string, secret: string) {
  await page.getByLabel('Email', { exact: true }).fill(email);
  await page.getByLabel('Senha', { exact: true }).fill(secret);
  await page.getByRole('button', { name: 'Entrar', exact: true }).click();
}

test('real SQL Server accounts, session, CSRF, logout and expiration over HTTPS', async ({ page, browser }, testInfo) => {
  await page.goto('/');
  await expect(page.getByRole('heading', { name: 'Entrar', exact: true })).toBeVisible();
  expect((await page.request.get('/api/me')).status()).toBe(401);
  const oldToken = await (await page.request.get('/api/auth/csrf')).json();
  expect((await page.request.post('/api/auth/login', { form: { email: 'brasil@example.invalid', password: password! } })).status()).toBe(403);

  await login(page, 'brasil@example.invalid', 'deliberately-wrong-password');
  await expect(page.getByTestId('auth-feedback')).toContainText('Confira seu email e senha');
  await expect(page.getByLabel('Senha', { exact: true })).toHaveValue('');
  await login(page, 'brasil@example.invalid', password!);
  await expect(page.getByRole('heading', { name: 'Olá, Colaborador Brasil' })).toBeVisible();
  const brazil = await (await page.request.get('/api/me')).json();
  expect(brazil.email).toBe('brasil@example.invalid');
  expect(brazil.referenceTimeZoneId).toBe('America/Sao_Paulo');
  expect(brazil.passwordHash).toBeUndefined();
  expect(brazil.password).toBeUndefined();
  const cookie = (await page.context().cookies()).find(item => item.name === 'JSESSIONID');
  expect(cookie?.secure).toBe(true);
  expect(cookie?.httpOnly).toBe(true);
  expect(cookie?.sameSite).toBe('Lax');
  await page.reload();
  await expect(page.getByRole('heading', { name: 'Olá, Colaborador Brasil' })).toBeVisible();
  expect((await page.request.post('/api/auth/logout')).status()).toBe(403);
  expect((await page.request.post('/api/auth/logout', { headers: { [oldToken.headerName]: oldToken.token } })).status()).toBe(403);
  expect((await page.request.get('/api/me')).status()).toBe(200);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  await page.screenshot({ path: testInfo.outputPath('authenticated.png'), fullPage: true });

  const europeContext = await browser.newContext({ baseURL: 'https://localhost:8443', ignoreHTTPSErrors: true });
  try {
    const europePage = await europeContext.newPage();
    await europePage.goto('/');
    await login(europePage, 'europa@example.invalid', password!);
    await expect(europePage.getByRole('heading', { name: 'Olá, Colaborador Europa' })).toBeVisible();
    const europe = await (await europePage.request.get('/api/me')).json();
    expect(europe.employeeId).not.toBe(brazil.employeeId);
    expect(europe.userId).not.toBe(brazil.userId);
    expect(europe.email).toBe('europa@example.invalid');
    expect(europe.referenceTimeZoneId).toBe('Europe/Amsterdam');
    expect((await (await page.request.get('/api/me')).json()).email).toBe('brasil@example.invalid');
    await europePage.getByRole('button', { name: 'Sair', exact: true }).click();
    await expect(europePage.getByRole('heading', { name: 'Entrar', exact: true })).toBeVisible();
    expect((await europePage.request.get('/api/me')).status()).toBe(401);
  } finally { await europeContext.close(); }

  await page.getByRole('button', { name: 'Sair', exact: true }).click();
  await expect(page.getByTestId('auth-feedback')).toContainText('Você saiu');
  expect((await page.request.get('/api/me', { headers: { Cookie: `JSESSIONID=${cookie!.value}` } })).status()).toBe(401);
  await login(page, 'brasil@example.invalid', password!);
  await expect(page.getByRole('heading', { name: 'Olá, Colaborador Brasil' })).toBeVisible();
  // Backend test process uses a real one-minute idle timeout; do not touch its session.
  await new Promise(resolve => setTimeout(resolve, 35_000));
  await new Promise(resolve => setTimeout(resolve, 30_000));
  await page.getByRole('button', { name: 'Verificar acesso' }).click();
  await expect(page.getByRole('heading', { name: 'Entrar', exact: true })).toBeVisible();
  expect((await page.request.get('/api/me')).status()).toBe(401);
  await page.screenshot({ path: testInfo.outputPath('login.png'), fullPage: true });
});
