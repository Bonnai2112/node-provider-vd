import { expect, test } from '@playwright/test';

test('home_should_render_provision_button_when_loaded', async ({ page }) => {
    await page.goto('/');

    await expect(
        page.getByRole('heading', { name: /Node Provider/i }),
    ).toBeVisible();

    const provisionButton = page.getByTestId('provision-btn');
    await expect(provisionButton).toBeVisible();

    await provisionButton.click();

    await expect(page.getByRole('dialog')).toBeVisible();
    await expect(
        page.getByRole('heading', { name: /Provisionner un nœud/i }),
    ).toBeVisible();
});
