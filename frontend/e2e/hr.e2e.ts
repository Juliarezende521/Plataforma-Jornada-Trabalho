import { test, expect, Page } from '@playwright/test';
import { randomUUID } from 'node:crypto';

async function login(page: Page, email: string) {
  const password = process.env['EVALUATION_PASSWORD'];
  if (!password) throw new Error('Evaluation password required outside Git');
  await page.goto('/');
  await page.getByLabel('Email', { exact: true }).fill(email);
  await page.getByLabel('Senha', { exact: true }).fill(password);
  await page.getByRole('button', { name: 'Entrar', exact: true }).click();
  if (email === 'rh@example.invalid') {
    await expect(page.getByRole('heading', { name: 'Visão do RH', exact: true })).toBeVisible();
    await expect(page.getByRole('navigation', { name: 'Navegação das jornadas' })).toHaveCount(0);
    await expect(page.getByRole('button', { name: 'Registrar entrada', exact: true })).toHaveCount(0);
  } else {
    await expect(page.getByRole('button', { name: 'Registrar entrada', exact: true })).toBeEnabled();
  }
}

async function logout(page: Page) {
  await page.getByRole('button', { name: 'Sair', exact: true }).click();
  // Wait for the logout request and redirect before starting another login.
  await expect(page.getByRole('heading', { name: 'Entrar', exact: true })).toBeVisible();
  expect((await page.request.get('/api/me')).status()).toBe(401);
}

test.afterEach(async ({ page }) => {
  const response = await page.request.get('/api/me/work-sessions/current');
  if (response.status() !== 200) return;
  const snapshot = await response.json();
  if (!snapshot.session || snapshot.session.state === 'CLOSED') return;
  const csrf = await (await page.request.get('/api/auth/csrf')).json();
  const exit = await page.request.post('/api/me/work-events', { headers: { [csrf.headerName]: csrf.token, 'Idempotency-Key': randomUUID() },
    data: { type: 'EXIT', expectedWorkSessionId: snapshot.session.id } });
  expect(exit.status()).toBe(201);
});

test('HR reads another employee history and detail while an employee remains isolated', async ({ page }, testInfo) => {
  await login(page, 'brasil@example.invalid');
  await expect(page.getByRole('button', { name: 'Visão do RH', exact: true })).toHaveCount(0);
  expect((await page.request.get('/api/hr/employees')).status()).toBe(403);
  const employee = (await (await page.request.get('/api/me')).json()).employeeId;
  await page.getByLabel('Modalidade de trabalho', { exact: true }).selectOption('TRAVEL');
  await page.getByRole('button', { name: 'Registrar entrada', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Encerrar jornada', exact: true })).toBeEnabled();
  const journey = (await (await page.request.get('/api/me/work-sessions/current')).json()).session;
  await page.getByRole('button', { name: 'Encerrar jornada', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Registrar entrada', exact: true })).toBeEnabled();
  await logout(page);
  await login(page, 'rh@example.invalid');
  await expect(page.getByRole('heading', { name: 'Visão do RH', exact: true })).toBeVisible();
  await expect(page.locator('.people li').filter({ hasText: 'Colaborador Brasil' })).toContainText('Encerrada');
  await page.getByRole('button', { name: 'Ver jornadas de Colaborador Brasil', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Histórico', exact: true })).toBeVisible();
  await expect(page.getByText('Jornadas de Colaborador Brasil', { exact: true })).toBeVisible();
  await expect(page.getByLabel('Data inicial', { exact: true })).toHaveValue(journey.workDate);
  await expect(page.getByLabel('Data final', { exact: true })).toHaveValue(journey.workDate);
  await page.getByRole('button', { name: 'Voltar à visão geral', exact: true }).click();
  await page.getByRole('button', { name: 'Consulta individual', exact: true }).click();
  await page.getByLabel('Colaborador', { exact: true }).selectOption(employee);
  await expect(page.getByText('Registros de', { exact: false })).toContainText('Colaborador Brasil');
  await page.getByLabel('Data inicial', { exact: true }).fill(journey.workDate);
  await page.getByLabel('Data final', { exact: true }).fill(journey.workDate);
  await page.getByRole('button', { name: 'Consultar período', exact: true }).click();
  await expect(page.getByTestId('history-feedback')).toContainText('Consulta concluída');
  await page.getByRole('button', { name: /^Ver detalhes da jornada de/ }).first().click();
  await expect(page.getByRole('heading', { name: 'Marcações registradas', exact: true })).toBeVisible();
  await expect(page.locator('.events li')).toHaveCount(2);
  await expect(page.locator('.event-work-mode').first()).toHaveText('Modalidade: Viagem');
  expect((await page.request.get('/api/me/work-sessions/' + journey.id)).status()).toBe(404);
  for (const width of [360, 1280]) {
    await page.setViewportSize({ width, height: 900 });
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
    await page.screenshot({ path: testInfo.outputPath(`hr-detail-${width}.png`), fullPage: true });
  }
  await logout(page);
  expect((await page.request.get('/api/hr/employees')).status()).toBe(401);
});

test('HR manages contract, schedule exception and reads their audit trail', async ({ page }, testInfo) => {
  const projectDay = testInfo.project.name === 'desktop' ? '01' : '02';
  const contractDate = `2027-01-${projectDay}`;
  const exceptionDate = `2027-01-${testInfo.project.name === 'desktop' ? '25' : '26'}`;
  await login(page, 'rh@example.invalid');
  const directory = await (await page.request.get('/api/hr/employees?size=20&page=0')).json();
  const employee = directory.items.find((item: { name: string }) => item.name === 'Colaborador Brasil');
  expect(employee).toBeTruthy();
  const csrf = await (await page.request.get('/api/auth/csrf')).json();
  const headers = { [csrf.headerName]: csrf.token };

  const contract = await page.request.post(`/api/hr/employees/${employee.id}/employment-contracts`, {
    headers,
    data: { effectiveFrom: contractDate, countryCode: 'BR', employmentType: 'FULL_TIME', weeklyMinutes: 2400 },
  });
  expect(contract.status()).toBe(201);
  const createdContract = await contract.json();
  const exception = await page.request.post(`/api/hr/employees/${employee.id}/work-schedule-exceptions`, {
    headers,
    data: { workDate: exceptionDate, plannedMinutes: 0, toleranceMinutes: 0, category: 'HOLIDAY', reason: 'Feriado local' },
  });
  expect(exception.status()).toBe(201);
  const createdException = await exception.json();
  expect((await page.request.get(`/api/hr/employees/${employee.id}/employment-contract?on=${contractDate}`)).status()).toBe(200);
  expect((await page.request.get(`/api/hr/employees/${employee.id}/work-schedule-exceptions?page=0&size=20`)).status()).toBe(200);

  await page.getByRole('button', { name: 'Auditoria', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Auditoria', exact: true })).toBeVisible();
  await expect(page.locator('.audit li').filter({ hasText: createdContract.id })).toContainText('Contrato cadastrado');
  await expect(page.locator('.audit li').filter({ hasText: createdException.id })).toContainText('Exceção de escala cadastrada');

  await logout(page);
  await login(page, 'brasil@example.invalid');
  const employeeCsrf = await (await page.request.get('/api/auth/csrf')).json();
  const denied = await page.request.post(`/api/hr/employees/${employee.id}/employment-contracts`, {
    headers: { [employeeCsrf.headerName]: employeeCsrf.token },
    data: { effectiveFrom: `2027-02-${projectDay}`, countryCode: 'BR', employmentType: 'FULL_TIME', weeklyMinutes: 2400 },
  });
  expect(denied.status()).toBe(403);
});
