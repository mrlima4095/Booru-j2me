import java.io.IOException;
import java.io.InputStream;

import javax.microedition.io.Connector;
import javax.microedition.io.HttpConnection;
import javax.microedition.lcdui.Alert;
import javax.microedition.lcdui.AlertType;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Form;
import javax.microedition.lcdui.Image;
import javax.microedition.lcdui.ImageItem;
import javax.microedition.lcdui.Item;
import javax.microedition.lcdui.ItemCommandListener;
import javax.microedition.lcdui.StringItem;
import javax.microedition.lcdui.TextField;
import javax.microedition.lcdui.Ticker;
import javax.microedition.midlet.MIDlet;

import cc.nnproject.json.JSON;
import cc.nnproject.json.JSONArray;
import cc.nnproject.json.JSONObject;

public class bIApp extends MIDlet implements Runnable, CommandListener, ItemCommandListener {
	
	private static final int RUN_POSTS = 1;
	private static final int RUN_SEARCH = 2;
	private static final int RUN_POST = 3;
	
	private static final String APIURL = "https://e621.net/";
	
	private static final Font smallfont = Font.getFont(0, 0, Font.SIZE_SMALL);
	private static final Font smallboldfont = Font.getFont(0, Font.STYLE_BOLD, Font.SIZE_SMALL);

	private static boolean started;
	private static bIApp midlet;
	private static Display display;
	
	private static Command exitCmd;
	private static Command searchCmd;
	private static Command postsCmd;
	
	private static Command backCmd;
	private static Command postItemCmd;
	
	private static Command showPostCmd;
	private static Command downloadCmd;

	private static Command prevPageCmd;
	private static Command nextPageCmd;
	private static Command nPageCmd;
	
	private static Form mainForm;
	private static Form postsForm;
	private static Form postForm;
	
	private static TextField searchField;
	
	private static int run;
	private static boolean running;
	
	private static String version;
	
	private static int limit = 10;
	private static int page = 1;
	private static String query;
	
	private static String proxyUrl = "http://nnp.nnchan.ru/proxy.php?";
	
	private static Image postPlaceholderImg = null;
	
	private static ImageItem postItem;
	private static JSONObject post;

	public bIApp() {
		midlet = this;
	}

	protected void destroyApp(boolean unconditional) {

	}

	protected void pauseApp() {

	}

	protected void startApp() {
		if (started) return;
		started = false;
		
		version = getAppProperty("MIDlet-Version");
		display = Display.getDisplay(this);
		
		exitCmd = new Command("Exit", Command.EXIT, 2);
		searchCmd = new Command("Search", Command.ITEM, 1);
		postsCmd = new Command("Posts", Command.ITEM, 1);

		backCmd = new Command("Back", Command.EXIT, 2);
		postItemCmd = new Command("Open", Command.ITEM, 1);
		nextPageCmd = new Command("Next page", Command.SCREEN, 2);
		prevPageCmd = new Command("Prev. page", Command.SCREEN, 3);
		nPageCmd = new Command("Go to page", Command.ITEM, 2);
		
		showPostCmd = new Command("Open", Command.ITEM, 1);
		downloadCmd = new Command("Download", Command.ITEM, 1);
		
		Form f = new Form("ы");
		f.addCommand(exitCmd);
		f.setCommandListener(this);
		
		StringItem s;
		
		s = new StringItem(null, "e621");
		s.setFont(Font.getFont(0, Font.STYLE_BOLD, Font.SIZE_LARGE));
		s.setLayout(Item.LAYOUT_CENTER | Item.LAYOUT_NEWLINE_AFTER);
		f.append(s);
		
		searchField = new TextField("Tags", "", 200, TextField.NON_PREDICTIVE);
		f.append(searchField);
		
		s = new StringItem(null, "Search", StringItem.BUTTON);
		s.setFont(Font.getDefaultFont());
		s.setLayout(Item.LAYOUT_EXPAND | Item.LAYOUT_NEWLINE_AFTER | Item.LAYOUT_NEWLINE_BEFORE);
		s.addCommand(searchCmd);
		s.setDefaultCommand(searchCmd);
		s.setItemCommandListener(this);
		f.append(s);
		
		s = new StringItem(null, "Posts", StringItem.BUTTON);
		s.setLayout(Item.LAYOUT_EXPAND | Item.LAYOUT_NEWLINE_AFTER | Item.LAYOUT_NEWLINE_BEFORE);
		s.addCommand(postsCmd);
		s.setDefaultCommand(postsCmd);
		s.setItemCommandListener(this);
		f.append(s);
		
		display.setCurrent(mainForm = f);
	}

	public void commandAction(Command c, Displayable d) {
		if (d == postsForm) {
			if (c == backCmd) {
				display(mainForm);
				postsForm = null;
				return;
			}
		}
		if (d == postForm) {
			if (c == backCmd) {
				display(postsForm);
				post = null;
				postForm = null;
				return;
			}
		}
		if (c == exitCmd) {
			notifyDestroyed();
			return;
		}
		if (c == backCmd) {
			display(mainForm);
			return;
		}
		if (c == nextPageCmd || c == prevPageCmd) {
			if (running) return;
			if (c == nextPageCmd) ++page;
			else {
				if (--page < 0) page = 0;
			}
			Form f = new Form("Posts");
			f.addCommand(backCmd);
			f.setCommandListener(this);
			
			f.setTicker(new Ticker("Loading..."));
			
			display(postsForm = f);
			start(query != null ? RUN_SEARCH : RUN_POSTS);
			return;
		}
	}

	public void commandAction(Command c, Item item) {
		if (c == showPostCmd) {
			try {
				String url = post.getObject("file").getString("url");
				if (platformRequest(url)) notifyDestroyed();
			} catch (Exception e) {
				e.printStackTrace();
				display(errorAlert(e.toString()), postForm);
			}
			return;
		}
		if (c == postItemCmd) {
			if (running) return;
			Form f = new Form("Post #" + (postItem = (ImageItem) item).getAltText());
			f.addCommand(backCmd);
			f.setCommandListener(this);
			
			display(postForm = f);
			start(RUN_POST);
			return;
		}
		if (c == searchCmd || c == postsCmd) {
			if (running) return;
			Form f = new Form("Posts");
			f.addCommand(backCmd);
			f.setCommandListener(this);
			
			f.setTicker(new Ticker("Loading..."));
			
			display(postsForm = f);
			query = c == searchCmd ? null : searchField.getString().trim();
			start(c == searchCmd ? RUN_SEARCH : RUN_POSTS);
			return;
		}
		if (c == nPageCmd) {
			if (running) return;
			page = Integer.parseInt(((StringItem) item).getText());
			Form f = new Form("Posts");
			f.addCommand(backCmd);
			f.setCommandListener(this);
			
			f.setTicker(new Ticker("Loading..."));
			
			display(postsForm = f);
			start(query != null ? RUN_SEARCH : RUN_POSTS);
			return;
		}
		commandAction(c, display.getCurrent());
	}

	public void run() {
		int run;
		synchronized(this) {
			run = bIApp.run;
			notify();
		}
		running = true;
		switch (run) {
		case RUN_SEARCH: 
		case RUN_POSTS: {
			try {
				Form f = postsForm;
				
				StringBuffer sb = new StringBuffer(run == RUN_SEARCH ? "Search" : "Posts");
				if (page > 1) {
					sb.append(" (").append(page).append(')');
					postsForm.addCommand(prevPageCmd);
					
					f.append(pageButton(-1));
				}
				
				postsForm.setTitle(sb.toString());
				
				sb.setLength(0);
				sb.append(APIURL).append("posts.json?limit=").append(limit);
				if (run == RUN_SEARCH) {
					sb.append("&tags=").append(url(query));
				}
				
				f.addCommand(nextPageCmd);
				
				if (page > 0) {
					sb.append("&page=").append(page);
				}
				
				f.append(pageButton(1));
				
				if (page > 2) f.append(pageButton(page - 1));
				if (page > 1) f.append(pageButton(page));
				f.append(pageButton(page + 1));
				if (page < 2) f.append(pageButton(page + 2));
				
				StringItem s = pageButton(0);
				s.setLayout(Item.LAYOUT_SHRINK | Item.LAYOUT_NEWLINE_AFTER);
				
				f.append(s);
				
				
				JSONArray posts = JSON.getObject(getUtf(proxyUrl(sb.toString()))).getArray("posts");
				
				ImageItem item;
				
				int l = posts.size();
				for (int i = 0; i < l && postsForm != null; i++) {
					JSONObject post = posts.getObject(i);
					
					item = new ImageItem("",
							postPlaceholderImg,
							Item.LAYOUT_LEFT | Item.LAYOUT_TOP,
							post.getString("id"));
					
					item.addCommand(postItemCmd);
					item.setDefaultCommand(postItemCmd);
					item.setItemCommandListener(this);
					
					String u = post.getObject("preview").getString("url");
					if (u != null) item.setImage(getImage(proxyUrl(u)));
					
					postsForm.append(item);
				}
			} catch (NullPointerException e) {
				break;
			} catch (Exception e) {
				e.printStackTrace();
				display(errorAlert(e.toString()), postsForm);
			}
			
			postsForm.setTicker(null);
			break;
		}
		case RUN_POST: {
			String id = postItem.getAltText();
			Image thumb = postItem.getImage();
			
			postItem = null;
			
			ImageItem item = new ImageItem("", thumb, Item.LAYOUT_LEFT, id);
			
			item.addCommand(showPostCmd);
			item.setDefaultCommand(showPostCmd);
			item.setItemCommandListener(this);
			postForm.append(item);
			
			try {
				StringBuffer sb = new StringBuffer(APIURL).append("posts/").append(id).append(".json");
				
				post = JSON.getObject(getUtf(proxyUrl(sb.toString()))).getObject("post");
				
				
				// TODO
				postForm.append(post.toString());
			} catch (Exception e) {
				e.printStackTrace();
				display(errorAlert(e.toString()), postForm);
			}
			break;
		}
		}
		running = false;
	}

	private void start(int i) {
		try {
			synchronized(this) {
				run = i;
				new Thread(this).start();
				wait();
			}
		} catch (Exception e) {}
	}
	
	private StringItem pageButton(int n) {
		StringItem s = new StringItem(null, n == -1 ? "<" : n == 0 ? ">" : Integer.toString(n), StringItem.BUTTON);
		s.setLayout(Item.LAYOUT_SHRINK);
		s.setFont(page == n ? smallboldfont : smallfont);
		Command c = n == -1 ? prevPageCmd : n == 0 ? nextPageCmd : nPageCmd;
		s.addCommand(c);
		s.setDefaultCommand(c);
		s.setItemCommandListener(this);
		return s;
	}
	
	private static void display(Alert a, Displayable d) {
		if(d == null) {
			display.setCurrent(a);
			return;
		}
		display.setCurrent(a, d);
	}

	private static void display(Displayable d) {
		if(d instanceof Alert) {
			display.setCurrent((Alert) d, mainForm);
			return;
		}
		display.setCurrent(d);
	}

	private static Alert errorAlert(String text) {
		Alert a = new Alert("");
		a.setType(AlertType.ERROR);
		a.setString(text);
		a.setTimeout(2000);
		return a;
	}
	
	// http

	private static Image getImage(String url) throws IOException {
		byte[] b = get(url);
		return Image.createImage(b, 0, b.length);
	}
	
	private static byte[] readBytes(InputStream inputStream, int initialSize, int bufferSize, int expandSize) throws IOException {
		if (initialSize <= 0) initialSize = bufferSize;
		byte[] buf = new byte[initialSize];
		int count = 0;
		byte[] readBuf = new byte[bufferSize];
		int readLen;
		while ((readLen = inputStream.read(readBuf)) != -1) {
			if(count + readLen > buf.length) {
				byte[] newbuf = new byte[count + expandSize];
				System.arraycopy(buf, 0, newbuf, 0, count);
				buf = newbuf;
			}
			System.arraycopy(readBuf, 0, buf, count, readLen);
			count += readLen;
		}
		if(buf.length == count) {
			return buf;
		}
		byte[] res = new byte[count];
		System.arraycopy(buf, 0, res, 0, count);
		return res;
	}
	
	private static byte[] get(String url) throws IOException {
		HttpConnection hc = null;
		InputStream in = null;
		try {
			hc = open(url);
			int r;
			if((r = hc.getResponseCode()) >= 400) {
				throw new IOException("HTTP " + r);
			}
			in = hc.openInputStream();
			return readBytes(in, (int) hc.getLength(), 8*1024, 16*1024);
		} finally {
			try {
				if (in != null) in.close();
			} catch (IOException e) {
			}
			try {
				if (hc != null) hc.close();
			} catch (IOException e) {
			}
		}
	}

	private static String getUtf(String url) throws IOException {
		HttpConnection hc = null;
		InputStream in = null;
		try {
			hc = open(url);
			int i, j, k = 0;
			if((i = hc.getResponseCode()) >= 400) {
				throw new IOException("HTTP " + i);
			}
			String r;
			while(i >= 300) {
				if(++k > 3) {
					throw new IOException("Too many redirects!");
				}
				if((r = hc.getHeaderField("Location")).startsWith("/")) {
					r = url.substring(0, (j = url.indexOf("//") + 2)) + url.substring(j, url.indexOf("/", j)) + r;
				}
				hc.close();
				hc = open(r);
				if((i = hc.getResponseCode()) >= 400) {
					throw new IOException("HTTP " + i);
				}
			}
			in = hc.openInputStream();
			byte[] buf = new byte[(i = (int) hc.getLength()) <= 0 ? 1024 : i];
			i = 0;
			while((j = in.read(buf, i, buf.length - i)) != -1) {
				if((i += j) == buf.length) {
					System.arraycopy(buf, 0, buf = new byte[i + 2048], 0, i);
				}
			}
			return new String(buf, 0, i, "UTF-8");
		} finally {
			try {
				if (in != null) in.close();
			} catch (IOException e) {}
			try {
				if (hc != null) hc.close();
			} catch (IOException e) {}
		}
	}
	
	private static HttpConnection open(String url) throws IOException {
		HttpConnection hc = (HttpConnection) Connector.open(url);
		hc.setRequestMethod("GET");
		hc.setRequestProperty("User-Agent", "j2me-client/" + version + " (https://github.com/shinovon)");
		return hc;
	}
	
	private static String proxyUrl(String url) {
		if(url == null || proxyUrl.length() == 0 || "https://".equals(proxyUrl)) {
			return url;
		}
		return proxyUrl + url(url);
	}
	
	public static String url(String url) {
		StringBuffer sb = new StringBuffer();
		char[] chars = url.toCharArray();
		for (int i = 0; i < chars.length; i++) {
			int c = chars[i];
			if (65 <= c && c <= 90) {
				sb.append((char) c);
			} else if (97 <= c && c <= 122) {
				sb.append((char) c);
			} else if (48 <= c && c <= 57) {
				sb.append((char) c);
			} else if (c == 32) {
				sb.append("%20");
			} else if (c == 45 || c == 95 || c == 46 || c == 33 || c == 126 || c == 42 || c == 39 || c == 40
					|| c == 41) {
				sb.append((char) c);
			} else if (c <= 127) {
				sb.append(hex(c));
			} else if (c <= 2047) {
				sb.append(hex(0xC0 | c >> 6));
				sb.append(hex(0x80 | c & 0x3F));
			} else {
				sb.append(hex(0xE0 | c >> 12));
				sb.append(hex(0x80 | c >> 6 & 0x3F));
				sb.append(hex(0x80 | c & 0x3F));
			}
		}
		return sb.toString();
	}

	private static String hex(int i) {
		String s = Integer.toHexString(i);
		return "%".concat(s.length() < 2 ? "0" : "").concat(s);
	}
	
	// image utils

	private static Image resize(Image src_i, int size_w, int size_h) {
		// set source size
		int w = src_i.getWidth();
		int h = src_i.getHeight();

		// no change??
		if (size_w == w && size_h == h)
			return src_i;

		int[] dst = new int[size_w * size_h];

		resize_rgb_filtered(src_i, dst, w, h, size_w, size_h);

		// not needed anymore
		src_i = null;

		return Image.createRGBImage(dst, size_w, size_h, true);
	}

	private static final void resize_rgb_filtered(Image src_i, int[] dst, int w0, int h0, int w1, int h1) {
		int[] buffer1 = new int[w0];
		int[] buffer2 = new int[w0];

		// UNOPTIMIZED bilinear filtering:               
		//         
		// The pixel position is defined by y_a and y_b,
		// which are 24.8 fixed point numbers
		// 
		// for bilinear interpolation, we use y_a1 <= y_a <= y_b1
		// and x_a1 <= x_a <= x_b1, with y_d and x_d defining how long
		// from x/y_b1 we are.
		//
		// since we are resizing one line at a time, we will at most 
		// need two lines from the source image (y_a1 and y_b1).
		// this will save us some memory but will make the algorithm 
		// noticeably slower

		for (int index1 = 0, y = 0; y < h1; y++) {

			final int y_a = ((y * h0) << 8) / h1;
			final int y_a1 = y_a >> 8;
			int y_d = y_a & 0xFF;

			int y_b1 = y_a1 + 1;
			if (y_b1 >= h0) {
				y_b1 = h0 - 1;
				y_d = 0;
			}

			// get the two affected lines:
			src_i.getRGB(buffer1, 0, w0, 0, y_a1, w0, 1);
			if (y_d != 0)
				src_i.getRGB(buffer2, 0, w0, 0, y_b1, w0, 1);

			for (int x = 0; x < w1; x++) {
				// get this and the next point
				int x_a = ((x * w0) << 8) / w1;
				int x_a1 = x_a >> 8;
				int x_d = x_a & 0xFF;

				int x_b1 = x_a1 + 1;
				if (x_b1 >= w0) {
					x_b1 = w0 - 1;
					x_d = 0;
				}

				// interpolate in x
				int c12, c34;
				int c1 = buffer1[x_a1];
				int c3 = buffer1[x_b1];

				// interpolate in y:
				if (y_d == 0) {
					c12 = c1;
					c34 = c3;
				} else {
					int c2 = buffer2[x_a1];
					int c4 = buffer2[x_b1];

					final int v1 = y_d & 0xFF;
					final int a_c2_RB = c1 & 0x00FF00FF;
					final int a_c2_AG_org = c1 & 0xFF00FF00;

					final int b_c2_RB = c3 & 0x00FF00FF;
					final int b_c2_AG_org = c3 & 0xFF00FF00;

					c12 = (a_c2_AG_org + ((((c2 >>> 8) & 0x00FF00FF) - (a_c2_AG_org >>> 8)) * v1)) & 0xFF00FF00
							| (a_c2_RB + ((((c2 & 0x00FF00FF) - a_c2_RB) * v1) >> 8)) & 0x00FF00FF;
					c34 = (b_c2_AG_org + ((((c4 >>> 8) & 0x00FF00FF) - (b_c2_AG_org >>> 8)) * v1)) & 0xFF00FF00
							| (b_c2_RB + ((((c4 & 0x00FF00FF) - b_c2_RB) * v1) >> 8)) & 0x00FF00FF;
				}

				// final result

				final int v1 = x_d & 0xFF;
				final int c2_RB = c12 & 0x00FF00FF;

				final int c2_AG_org = c12 & 0xFF00FF00;
				dst[index1++] = (c2_AG_org + ((((c34 >>> 8) & 0x00FF00FF) - (c2_AG_org >>> 8)) * v1)) & 0xFF00FF00
						| (c2_RB + ((((c34 & 0x00FF00FF) - c2_RB) * v1) >> 8)) & 0x00FF00FF;
			}
		}
	}
}