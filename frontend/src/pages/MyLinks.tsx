import { useEffect, useState } from 'react';
import { Container, Title, Table, Group, ActionIcon, Text, Pagination, Badge, Tooltip, Modal, Paper, Stack, Checkbox, Button, Switch, useMantineTheme } from '@mantine/core';
import { IconCopy, IconChartBar, IconTrash, IconSettings } from '@tabler/icons-react';
import { urlApi } from '../api/urls';
import { analyticsApi } from '../api/analytics';
import { apiClient, type UrlSummaryResponse, type PageResponse, type AnalyticsResponse } from '../api/client';
import { notifications } from '@mantine/notifications';
import { useDisclosure } from '@mantine/hooks';

export default function MyLinks() {
  const theme = useMantineTheme();
  const [page, setPage] = useState(0);
  const [urls, setUrls] = useState<PageResponse<UrlSummaryResponse> | null>(null);
  const [loading, setLoading] = useState(true);
  
  const [opened, { open, close }] = useDisclosure(false);
  const [selectedAnalytics, setSelectedAnalytics] = useState<AnalyticsResponse | null>(null);
  const [analyticsLoading, setAnalyticsLoading] = useState(false);

  const [selected, setSelected] = useState<string[]>([]);

  const fetchUrls = async () => {
    try {
      setLoading(true);
      const data = await urlApi.getMyUrls(page, 10);
      setUrls(data);
    } catch (err) {
      notifications.show({ title: 'Error', message: 'Failed to fetch URLs', color: 'red' });
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchUrls();
  }, [page]);

  const handleCopy = (shortUrl: string) => {
    navigator.clipboard.writeText(shortUrl);
    notifications.show({ title: 'Copied', message: 'URL copied to clipboard', color: 'teal' });
  };

  const handleDelete = async (id: string) => {
    if (!confirm('Are you sure you want to delete this link?')) return;
    try {
      await urlApi.deleteUrl(id);
      notifications.show({ title: 'Deleted', message: 'Link deleted successfully', color: 'teal' });
      fetchUrls();
    } catch (err) {
      notifications.show({ title: 'Error', message: 'Failed to delete link', color: 'red' });
    }
  };

  const handleBulkDelete = async () => {
    if (selected.length === 0) return;
    if (!confirm(`Are you sure you want to delete ${selected.length} selected links?`)) return;
    try {
      await apiClient.delete('/urls/bulk', { data: selected });
      notifications.show({ title: 'Deleted', message: `${selected.length} links deleted`, color: 'teal' });
      setSelected([]);
      fetchUrls();
    } catch (err) {
      notifications.show({ title: 'Error', message: 'Failed to delete links', color: 'red' });
    }
  };

  const toggleStatus = async (url: UrlSummaryResponse) => {
    try {
      await urlApi.updateUrl(url.id, { active: !url.active });
      notifications.show({ title: 'Updated', message: `Link ${!url.active ? 'enabled' : 'disabled'} successfully`, color: 'teal' });
      fetchUrls();
    } catch (err) {
      notifications.show({ title: 'Error', message: 'Failed to update status', color: 'red' });
    }
  };

  const toggleRow = (id: string) => setSelected((s) => s.includes(id) ? s.filter(x => x !== id) : [...s, id]);
  const toggleAll = () => {
    if (urls?.content.length === selected.length) {
      setSelected([]);
    } else {
      setSelected(urls?.content.map(u => u.id) || []);
    }
  };

  const viewAnalytics = async (id: string) => {
    open();
    setAnalyticsLoading(true);
    try {
      const data = await analyticsApi.getAnalytics(id);
      setSelectedAnalytics(data);
    } catch (err) {
      notifications.show({ title: 'Error', message: 'Failed to fetch analytics', color: 'red' });
      close();
    } finally {
      setAnalyticsLoading(false);
    }
  };

  return (
    <Container size="xl" mt="xl">
      <Group justify="space-between" mb="lg">
        <Title order={2}>My Links</Title>
        {selected.length > 0 && (
          <Button color="red" leftSection={<IconTrash size={16} />} onClick={handleBulkDelete}>
            Delete Selected ({selected.length})
          </Button>
        )}
      </Group>
      
      <Table striped highlightOnHover withTableBorder>
        <Table.Thead>
          <Table.Tr>
            <Table.Th w={40}>
              <Checkbox 
                checked={urls?.content.length > 0 && selected.length === urls?.content.length}
                indeterminate={selected.length > 0 && selected.length !== urls?.content.length}
                onChange={toggleAll}
              />
            </Table.Th>
            <Table.Th>Title / Original URL</Table.Th>
            <Table.Th>Short Code</Table.Th>
            <Table.Th>Clicks</Table.Th>
            <Table.Th>Status</Table.Th>
            <Table.Th>Actions</Table.Th>
          </Table.Tr>
        </Table.Thead>
        <Table.Tbody>
          {loading ? (
            <Table.Tr>
              <Table.Td colSpan={6} ta="center">Loading...</Table.Td>
            </Table.Tr>
          ) : urls?.content.map((url) => (
            <Table.Tr key={url.id}>
              <Table.Td>
                <Checkbox 
                  checked={selected.includes(url.id)}
                  onChange={() => toggleRow(url.id)}
                />
              </Table.Td>
              <Table.Td>
                <Text fw={500}>{url.title || 'Untitled'}</Text>
                <Text size="xs" c="dimmed" truncate w={250}>{url.originalUrl}</Text>
              </Table.Td>
              <Table.Td>
                <Group gap="xs">
                  <Text>{url.shortCode}</Text>
                  <ActionIcon variant="subtle" size="sm" onClick={() => handleCopy(url.shortUrl)}>
                    <IconCopy size={14} />
                  </ActionIcon>
                </Group>
              </Table.Td>
              <Table.Td>{url.clickCount}</Table.Td>
              <Table.Td>
                <Group gap="xs" align="center">
                  <Switch 
                    size="sm" 
                    checked={url.active} 
                    onChange={() => toggleStatus(url)} 
                    color="teal" 
                  />
                  <Badge color={url.active ? 'teal' : 'red'}>{url.active ? 'Active' : 'Disabled'}</Badge>
                </Group>
              </Table.Td>
              <Table.Td>
                <Group gap="xs">
                  <Tooltip label="Analytics">
                    <ActionIcon variant="light" onClick={() => viewAnalytics(url.id)}>
                      <IconChartBar size={16} />
                    </ActionIcon>
                  </Tooltip>
                  <Tooltip label="Delete">
                    <ActionIcon variant="light" color="red" onClick={() => handleDelete(url.id)}>
                      <IconTrash size={16} />
                    </ActionIcon>
                  </Tooltip>
                </Group>
              </Table.Td>
            </Table.Tr>
          ))}
          {urls?.content.length === 0 && (
             <Table.Tr>
              <Table.Td colSpan={6} ta="center">You have no shortened links.</Table.Td>
            </Table.Tr>
          )}
        </Table.Tbody>
      </Table>
      
      {urls && urls.page.totalPages > 1 && (
        <Group justify="center" mt="md">
          <Pagination total={urls.page.totalPages} value={page + 1} onChange={(p) => setPage(p - 1)} />
        </Group>
      )}

      <Modal opened={opened} onClose={close} title="Link Analytics" size="lg">
        {analyticsLoading ? (
          <Text>Loading analytics...</Text>
        ) : selectedAnalytics ? (
          <Stack>
            <Paper withBorder p="md" bg={`${theme.primaryColor}.1`}>
              <Title order={3} ta="center" c={theme.primaryColor}>Total Clicks: {selectedAnalytics.totalClicks}</Title>
            </Paper>
            <Title order={4}>Recent Clicks</Title>
            {selectedAnalytics.recentClicks.length === 0 ? (
              <Text c="dimmed">No clicks recorded yet.</Text>
            ) : (
              <Table striped>
                <Table.Thead>
                  <Table.Tr>
                    <Table.Th>Time</Table.Th>
                    <Table.Th>IP</Table.Th>
                    <Table.Th>Device/OS</Table.Th>
                    <Table.Th>Browser</Table.Th>
                  </Table.Tr>
                </Table.Thead>
                <Table.Tbody>
                  {selectedAnalytics.recentClicks.map((click, i) => (
                    <Table.Tr key={i}>
                      <Table.Td>{new Date(click.clickedAt).toLocaleString()}</Table.Td>
                      <Table.Td>{click.ipAddress}</Table.Td>
                      <Table.Td>{click.device} / {click.os}</Table.Td>
                      <Table.Td>{click.browser}</Table.Td>
                    </Table.Tr>
                  ))}
                </Table.Tbody>
              </Table>
            )}
          </Stack>
        ) : null}
      </Modal>
    </Container>
  );
}
