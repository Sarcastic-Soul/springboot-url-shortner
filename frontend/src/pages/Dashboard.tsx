import { useState } from 'react';
import { Card, CardHeader, CardContent } from '../components/ui/Card';
import { Skeleton } from '../components/ui/Skeleton';
import { Input, TextArea, TextField, Label } from '../components/ui/Input';
import { Button } from '../components/ui/Button';
import { Select, ListBox } from '../components/ui/Select';
import { Accordion } from '../components/ui/Accordion';
import { Table, TableHeader, TableBody, TableColumn, TableRow, TableCell } from '../components/ui/Table';
import { IconCheck, IconCopy, IconX, IconSettings } from '@tabler/icons-react';
import toast from 'react-hot-toast';
import { urlApi } from '../api/urls';
import type { UrlResponse } from '../api/client';
import { useAuth } from '../contexts/AuthContext';
import { useNavigate } from 'react-router-dom';

export default function Dashboard() {
  const [url, setUrl] = useState('');
  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState<UrlResponse | null>(null);
  const [expiryDays, setExpiryDays] = useState<string>('30');

  // Advanced Features
  const [customAlias, setCustomAlias] = useState('');
  const [linkTitle, setLinkTitle] = useState('');
  const [description, setDescription] = useState('');
  const [tags, setTags] = useState('');
  const [password, setPassword] = useState('');
  const [maxClicks, setMaxClicks] = useState<string>('');

  const { isAuthenticated } = useAuth();
  const navigate = useNavigate();

  const handleShorten = async () => {
    if (!url) return;

    setLoading(true);
    setResult(null);

    try {
      let formattedUrl = url.trim();
      if (!formattedUrl.startsWith('http://') && !formattedUrl.startsWith('https://')) {
        formattedUrl = 'https://' + formattedUrl;
      }

      const days = isAuthenticated ? parseInt(expiryDays, 10) : 7;
      const expiryDate = new Date();
      expiryDate.setDate(expiryDate.getDate() + days);
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
      toast.success('URL shortened successfully!');
    } catch (err: any) {
      const errorMsg = err.response?.data?.error || err.response?.data?.message || 'Failed to shorten URL. Please try again.';
      toast.error(errorMsg);
    } finally {
      setLoading(false);
    }
  };

  const copyToClipboard = (text: string) => {
    navigator.clipboard.writeText(text);
    toast.success('Copied to clipboard!');
  };

  return (
    <div className="container mx-auto px-4 max-w-3xl mt-12 mb-20">
      <Card className="p-6">
        <CardHeader className="flex-col items-center gap-2 mb-4">
          <h1 className="text-4xl font-bold text-primary">TrimURL</h1>
          <p className="text-default-500 text-center">
            The industry-grade platform to shorten your links and track analytics.
          </p>
        </CardHeader>

        <CardContent className="gap-6 flex flex-col">
          <div className="flex flex-col sm:flex-row gap-4 items-end">
            <div className="flex-1 w-full flex flex-col gap-1">
              <TextField
                isDisabled={loading}
                value={url}
                onChange={setUrl}
                className="w-full flex flex-col gap-1"
              >
                <Label className="text-sm font-medium">Enter URL</Label>
                <Input
                  type="text"
                  placeholder="https://your-long-url.com/very/long/path"
                  className="w-full text-lg h-12"
                />
              </TextField>
            </div>
            {isAuthenticated && (
              <div className="w-full sm:w-32 flex flex-col gap-1">
                <div className="flex flex-col gap-1 text-sm font-medium">
                  <label>Expiry</label>
                  <Select.Root
                    selectedKey={expiryDays}
                    onSelectionChange={(k) => setExpiryDays(k as string)}
                  >
                    <Select.Trigger className="h-12 bg-default-100 hover:bg-default-200 transition-colors border-none rounded-lg px-3 flex justify-between items-center w-full">
                      <Select.Value />
                      <Select.Indicator />
                    </Select.Trigger>
                    <Select.Popover>
                      <ListBox>
                        <ListBox.Item id="1" textValue="1 Day">1 Day</ListBox.Item>
                        <ListBox.Item id="7" textValue="7 Days">7 Days</ListBox.Item>
                        <ListBox.Item id="30" textValue="30 Days">30 Days</ListBox.Item>
                      </ListBox>
                    </Select.Popover>
                  </Select.Root>
                </div>
              </div>
            )}
            <Button
              onPress={handleShorten}
              isDisabled={loading}
              variant="primary"
              className="w-full sm:w-auto h-12 text-lg"
            >
              {loading ? "Shortening..." : "Shorten"}
            </Button>
          </div>

          {isAuthenticated && (
            <div className="w-full mt-4">
              <Accordion variant="default">
                <Accordion.Item id="1" aria-label="Advanced Options">
                  <Accordion.Trigger className="font-medium">
                    <div className="flex items-center gap-2">
                      <IconSettings size={18} />
                      <span>Advanced Options</span>
                    </div>
                  </Accordion.Trigger>
                  <Accordion.Panel className="pb-4">
                    <div className="flex flex-col gap-4">
                      <div className="flex flex-col sm:flex-row gap-4">
                        <TextField
                          value={customAlias}
                          onChange={setCustomAlias}
                          className="flex-1 flex flex-col gap-1"
                        >
                          <Label className="text-sm font-medium">Custom Alias</Label>
                          <Input placeholder="e.g. my-campaign" />
                        </TextField>
                        <TextField
                          value={password}
                          onChange={setPassword}
                          className="flex-1 flex flex-col gap-1"
                        >
                          <Label className="text-sm font-medium">Password Protection</Label>
                          <Input type="password" placeholder="Secret code" />
                        </TextField>
                      </div>
                      <div className="flex flex-col sm:flex-row gap-4">
                        <TextField
                          value={linkTitle}
                          onChange={setLinkTitle}
                          className="flex-1 flex flex-col gap-1"
                        >
                          <Label className="text-sm font-medium">Title</Label>
                          <Input placeholder="Campaign Title" />
                        </TextField>
                        <TextField
                          value={tags}
                          onChange={setTags}
                          className="flex-1 flex flex-col gap-1"
                        >
                          <Label className="text-sm font-medium">Tags</Label>
                          <Input placeholder="social, marketing (comma separated)" />
                        </TextField>
                      </div>
                      <TextField
                        value={maxClicks}
                        onChange={setMaxClicks}
                        className="w-full flex flex-col gap-1"
                      >
                        <Label className="text-sm font-medium">Max Clicks (optional)</Label>
                        <Input type="number" placeholder="Limit number of uses" min="1" />
                      </TextField>
                      <TextField
                        value={description}
                        onChange={setDescription}
                        className="w-full flex flex-col gap-1"
                      >
                        <Label className="text-sm font-medium">Description</Label>
                        <TextArea placeholder="Optional notes for this link" rows={3} />
                      </TextField>
                    </div>
                  </Accordion.Panel>
                </Accordion.Item>
              </Accordion>
            </div>
          )}

          {loading && (
            <Card className="border border-gray-200 dark:border-slate-800 mt-4 shadow-none">
              <CardContent className="flex flex-row justify-between items-center py-4 px-6 gap-4">
                 <Skeleton className="w-full max-w-sm h-6" />
                 <Skeleton className="w-8 h-8 rounded-md" />
              </CardContent>
            </Card>
          )}

          {!loading && result && (
            <Card className="border border-success/30 bg-success/5 mt-4 shadow-none">
              <CardContent className="flex flex-row justify-between items-center py-4 px-6">
                <span className="font-medium text-lg truncate pr-4 text-success">
                  {result.shortUrl || `http://localhost:8080/${result.shortCode}`}
                </span>
                <Button
                  isIconOnly
                  variant="ghost"
                  aria-label="Copy URL"
                  onPress={() => copyToClipboard(result.shortUrl || `http://localhost:8080/${result.shortCode}`)}
                >
                  <IconCopy size={20} />
                </Button>
              </CardContent>
            </Card>
          )}
        </CardContent>
      </Card>

      {!isAuthenticated && (
        <Card className="mt-12 p-6">
          <CardHeader className="justify-center">
            <h2 className="text-2xl font-bold">Why Create an Account?</h2>
          </CardHeader>
          <CardContent>
            <Table aria-label="Features comparison table">
              <TableHeader>
                <TableColumn>FEATURE</TableColumn>
                <TableColumn className="text-center">ANONYMOUS</TableColumn>
                <TableColumn className="text-center">REGISTERED USER</TableColumn>
              </TableHeader>
              <TableBody>
                <TableRow>
                  <TableCell>Custom Aliases</TableCell>
                  <TableCell><div className="flex justify-center text-danger"><IconX size={18} /></div></TableCell>
                  <TableCell><div className="flex justify-center text-success"><IconCheck size={18} /></div></TableCell>
                </TableRow>
                <TableRow>
                  <TableCell>Link Expiry</TableCell>
                  <TableCell><div className="text-center text-default-500">Max 7 days</div></TableCell>
                  <TableCell><div className="text-center text-success font-medium">Up to 30 days</div></TableCell>
                </TableRow>
                <TableRow>
                  <TableCell>Analytics Dashboard</TableCell>
                  <TableCell><div className="flex justify-center text-danger"><IconX size={18} /></div></TableCell>
                  <TableCell><div className="flex justify-center text-success"><IconCheck size={18} /></div></TableCell>
                </TableRow>
                <TableRow>
                  <TableCell>Manage Links</TableCell>
                  <TableCell><div className="flex justify-center text-danger"><IconX size={18} /></div></TableCell>
                  <TableCell><div className="flex justify-center text-success"><IconCheck size={18} /></div></TableCell>
                </TableRow>
                <TableRow>
                  <TableCell>Password Protection</TableCell>
                  <TableCell><div className="flex justify-center text-danger"><IconX size={18} /></div></TableCell>
                  <TableCell><div className="flex justify-center text-success"><IconCheck size={18} /></div></TableCell>
                </TableRow>
                <TableRow>
                  <TableCell>Click Limits</TableCell>
                  <TableCell><div className="flex justify-center text-danger"><IconX size={18} /></div></TableCell>
                  <TableCell><div className="flex justify-center text-success"><IconCheck size={18} /></div></TableCell>
                </TableRow>
                <TableRow>
                  <TableCell>Tags & Metadata</TableCell>
                  <TableCell><div className="flex justify-center text-danger"><IconX size={18} /></div></TableCell>
                  <TableCell><div className="flex justify-center text-success"><IconCheck size={18} /></div></TableCell>
                </TableRow>
              </TableBody>
            </Table>
            <div className="flex justify-center mt-8">
              <Button
                variant="primary"
                className="h-12 text-lg px-8"
                onPress={() => navigate('/register')}
              >
                Create Free Account
              </Button>
            </div>
          </CardContent>
        </Card>
      )}
    </div>
  );
}
