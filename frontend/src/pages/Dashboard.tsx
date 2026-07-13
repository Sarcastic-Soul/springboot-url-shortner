import { Container, Title, Text, TextInput, Button, Group, Paper, Stack, CopyButton, ActionIcon, Select, Table, ThemeIcon, Accordion, PasswordInput, NumberInput, Textarea } from '@mantine/core';
import { IconCheck, IconCopy, IconX, IconSettings } from '@tabler/icons-react';
import { notifications } from '@mantine/notifications';
import { useState } from 'react';
import { urlApi } from '../api/urls';
import type { UrlResponse } from '../api/client';
import { useAuth } from '../contexts/AuthContext';
import { useNavigate } from 'react-router-dom';

export default function Dashboard() {
  const [url, setUrl] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<UrlResponse | null>(null);
  const [expiryDays, setExpiryDays] = useState<string>('30');
  
  // Advanced Features
  const [customAlias, setCustomAlias] = useState('');
  const [linkTitle, setLinkTitle] = useState('');
  const [description, setDescription] = useState('');
  const [tags, setTags] = useState('');
  const [password, setPassword] = useState('');
  const [maxClicks, setMaxClicks] = useState<number | ''>('');

  const { isAuthenticated } = useAuth();
  const navigate = useNavigate();

  const handleShorten = async () => {
    if (!url) return;
    
    setLoading(true);
    setError(null);
    setResult(null);
    
    try {
      let formattedUrl = url.trim();
      if (!formattedUrl.startsWith('http://') && !formattedUrl.startsWith('https://')) {
        formattedUrl = 'https://' + formattedUrl;
      }
      
      const days = isAuthenticated ? parseInt(expiryDays, 10) : 7;
      const expiryDate = new Date();
      expiryDate.setDate(expiryDate.getDate() + days);
      // Subtract a few minutes to prevent any strict backend clock drift failures
      expiryDate.setMinutes(expiryDate.getMinutes() - 5);
      
      const payload: any = { 
        originalUrl: formattedUrl,
        expiresAt: expiryDate.toISOString()
      };

      if (isAuthenticated) {
        if (customAlias) payload.customAlias = customAlias;
        if (linkTitle) payload.title = linkTitle;
        if (description) payload.description = description;
        if (tags) payload.tags = tags;
        if (password) payload.password = password;
        if (maxClicks) payload.maxClicks = Number(maxClicks);
      }

      const response = await urlApi.createUrl(payload);
      setResult(response);
      setUrl('');
      setCustomAlias('');
      setLinkTitle('');
      setDescription('');
      setTags('');
      setPassword('');
      setMaxClicks('');
    } catch (err: any) {
      const errorMsg = err.response?.data?.error || err.response?.data?.message || 'Failed to shorten URL. Please try again.';
      setError(errorMsg);
      notifications.show({
        title: 'Error',
        message: errorMsg,
        color: 'red',
      });
    } finally {
      setLoading(false);
    }
  };

  return (
    <Container size="sm" mt={40}>
      <Paper shadow="md" radius="md" p="xl">
        <Stack align="center" gap="lg">
          <div>
            <Title order={1} ta="center">
              <Text inherit c="blue" component="span">
                TrimURL
              </Text>
            </Title>
            <Text c="dimmed" ta="center" mt="sm">
              The industry-grade platform to shorten your links and track analytics.
            </Text>
          </div>

          <Group w="100%" gap="sm" align="flex-end">
            <TextInput
              placeholder="https://your-long-url.com/very/long/path"
              label="Enter URL"
              value={url}
              onChange={(event) => setUrl(event.currentTarget.value)}
              style={{ flex: 1 }}
              size="md"
              disabled={loading}
            />
            {isAuthenticated && (
              <Select
                label="Expiry"
                data={[
                  { value: '1', label: '1 Day' },
                  { value: '7', label: '7 Days' },
                  { value: '30', label: '30 Days' },
                ]}
                value={expiryDays}
                onChange={(val) => setExpiryDays(val || '30')}
                size="md"
                w={120}
              />
            )}
            <Button 
              size="md" 
              onClick={handleShorten} 
              loading={loading}
              color="blue"
            >
              Shorten
            </Button>
          </Group>

          {isAuthenticated && (
            <Accordion variant="separated" w="100%" mt="xs">
              <Accordion.Item value="advanced">
                <Accordion.Control icon={<IconSettings size={18} />}>
                  Advanced Options
                </Accordion.Control>
                <Accordion.Panel>
                  <Stack gap="sm">
                    <Group grow>
                      <TextInput 
                        label="Custom Alias" 
                        placeholder="e.g. my-campaign" 
                        value={customAlias}
                        onChange={(e) => setCustomAlias(e.currentTarget.value)}
                      />
                      <PasswordInput 
                        label="Password Protection" 
                        placeholder="Secret code" 
                        value={password}
                        onChange={(e) => setPassword(e.currentTarget.value)}
                      />
                    </Group>
                    <Group grow>
                      <TextInput 
                        label="Title" 
                        placeholder="Campaign Title" 
                        value={linkTitle}
                        onChange={(e) => setLinkTitle(e.currentTarget.value)}
                      />
                      <TextInput 
                        label="Tags" 
                        placeholder="social, marketing (comma separated)" 
                        value={tags}
                        onChange={(e) => setTags(e.currentTarget.value)}
                      />
                    </Group>
                    <NumberInput 
                      label="Max Clicks (optional)" 
                      placeholder="Limit number of uses" 
                      value={maxClicks}
                      onChange={(val) => setMaxClicks(val)}
                      min={1}
                    />
                    <Textarea 
                      label="Description" 
                      placeholder="Optional notes for this link" 
                      value={description}
                      onChange={(e) => setDescription(e.currentTarget.value)}
                    />
                  </Stack>
                </Accordion.Panel>
              </Accordion.Item>
            </Accordion>
          )}

          {result && (
            <Paper withBorder p="md" w="100%">
              <Group justify="space-between">
                <Text fw={500}>{result.shortUrl || `http://localhost:8080/${result.shortCode}`}</Text>
                <CopyButton value={result.shortUrl || `http://localhost:8080/${result.shortCode}`} timeout={2000}>
                  {({ copied, copy }) => (
                    <ActionIcon color={copied ? 'teal' : 'gray'} variant="subtle" onClick={copy}>
                      {copied ? <IconCheck size={16} /> : <IconCopy size={16} />}
                    </ActionIcon>
                  )}
                </CopyButton>
              </Group>
            </Paper>
          )}
        </Stack>
      </Paper>

      {!isAuthenticated && (
        <Paper mt="xl" p="xl" radius="md" shadow="md">
          <Title order={3} ta="center" mb="xl">Why Create an Account?</Title>
          <Table verticalSpacing="sm" striped>
            <Table.Thead>
              <Table.Tr>
                <Table.Th>Feature</Table.Th>
                <Table.Th ta="center">Anonymous</Table.Th>
                <Table.Th ta="center">Registered User</Table.Th>
              </Table.Tr>
            </Table.Thead>
            <Table.Tbody>
              <Table.Tr>
                <Table.Td>Custom Aliases</Table.Td>
                <Table.Td ta="center"><ThemeIcon color="red" variant="light" size="sm"><IconX size={14}/></ThemeIcon></Table.Td>
                <Table.Td ta="center"><ThemeIcon color="teal" variant="light" size="sm"><IconCheck size={14}/></ThemeIcon></Table.Td>
              </Table.Tr>
              <Table.Tr>
                <Table.Td>Link Expiry</Table.Td>
                <Table.Td ta="center">Max 7 days</Table.Td>
                <Table.Td ta="center">Up to 30 days</Table.Td>
              </Table.Tr>
              <Table.Tr>
                <Table.Td>Analytics Dashboard</Table.Td>
                <Table.Td ta="center"><ThemeIcon color="red" variant="light" size="sm"><IconX size={14}/></ThemeIcon></Table.Td>
                <Table.Td ta="center"><ThemeIcon color="teal" variant="light" size="sm"><IconCheck size={14}/></ThemeIcon></Table.Td>
              </Table.Tr>
              <Table.Tr>
                <Table.Td>Manage Links</Table.Td>
                <Table.Td ta="center"><ThemeIcon color="red" variant="light" size="sm"><IconX size={14}/></ThemeIcon></Table.Td>
                <Table.Td ta="center"><ThemeIcon color="teal" variant="light" size="sm"><IconCheck size={14}/></ThemeIcon></Table.Td>
              </Table.Tr>
              <Table.Tr>
                <Table.Td>Password Protection</Table.Td>
                <Table.Td ta="center"><ThemeIcon color="red" variant="light" size="sm"><IconX size={14}/></ThemeIcon></Table.Td>
                <Table.Td ta="center"><ThemeIcon color="teal" variant="light" size="sm"><IconCheck size={14}/></ThemeIcon></Table.Td>
              </Table.Tr>
              <Table.Tr>
                <Table.Td>Click Limits</Table.Td>
                <Table.Td ta="center"><ThemeIcon color="red" variant="light" size="sm"><IconX size={14}/></ThemeIcon></Table.Td>
                <Table.Td ta="center"><ThemeIcon color="teal" variant="light" size="sm"><IconCheck size={14}/></ThemeIcon></Table.Td>
              </Table.Tr>
              <Table.Tr>
                <Table.Td>Tags & Metadata</Table.Td>
                <Table.Td ta="center"><ThemeIcon color="red" variant="light" size="sm"><IconX size={14}/></ThemeIcon></Table.Td>
                <Table.Td ta="center"><ThemeIcon color="teal" variant="light" size="sm"><IconCheck size={14}/></ThemeIcon></Table.Td>
              </Table.Tr>
            </Table.Tbody>
          </Table>
          <Group justify="center" mt="lg">
            <Button color="blue" onClick={() => navigate('/register')}>
              Create Free Account
            </Button>
          </Group>
        </Paper>
      )}
    </Container>
  );
}
