# ToyApi Developer Portal

A simple, self-contained web portal for developers to register and manage their API keys for the ToyApi service.

## Features

### 🎯 **Developer Registration**
- Self-service developer account creation
- Capture developer information (name, email, organization, purpose)
- Immediate access to API key management

### 🔑 **API Key Management**
- Create new API keys with custom names
- View all API keys with creation dates and usage
- Delete unused API keys
- Real-time key status tracking

### 👤 **Profile Management**
- View developer profile information
- Update profile details
- Track membership duration

### 📖 **Integrated Documentation**
- Complete API reference
- Usage examples (JavaScript, cURL)
- Rate limiting information
- Authentication guidelines

## Usage

### Local Development
1. Open `index.html` in any modern web browser
2. The portal connects directly to the deployed ToyApi endpoints
3. No build process or server required

### Deployment Options

#### Static Hosting (Recommended)
Deploy to any static hosting service:
- **AWS S3 + CloudFront**
- **Netlify**
- **Vercel**
- **GitHub Pages**

#### Web Server
Serve with any web server:
```bash
# Python
python -m http.server 8000

# Node.js
npx serve .

# nginx/Apache
# Simply place files in web root
```

## Configuration

The portal is configured to work with the production ToyApi deployment:
- **API Base URL**: `https://785sk4gpbh.execute-api.us-east-1.amazonaws.com/dev`
- **Environment**: Development (can be easily changed for staging/production)

To modify for different environments, update the `API_BASE_URL` constant in the JavaScript section.

## API Integration

The portal integrates with these ToyApi endpoints:

### Developer Management
- `POST /developer/register` - Register new developer
- `GET /developer/profile` - Get developer profile
- `PUT /developer/profile` - Update developer profile

### API Key Management
- `POST /developer/api-key` - Create new API key
- `GET /developer/api-keys` - List developer's API keys
- `DELETE /developer/api-key/{keyId}` - Delete API key

## Security Considerations

### Current Implementation
- **Public Endpoints**: Developer registration and key management use public endpoints
- **Email-based Access**: Developers access their data using email addresses
- **No Sensitive Data Exposure**: API key values are only shown once during creation

### Production Recommendations
For production use, consider adding:
- **Developer Authentication**: Implement proper login/session management
- **HTTPS Only**: Ensure all traffic is encrypted
- **Input Validation**: Add client-side and server-side validation
- **Rate Limiting**: Implement portal-specific rate limiting
- **Audit Logging**: Track developer actions for security monitoring

## Browser Compatibility

- **Modern Browsers**: Chrome 60+, Firefox 55+, Safari 11+, Edge 79+
- **Features Used**: Fetch API, CSS Grid, ES6 Arrow Functions
- **Mobile Responsive**: Works on tablets and mobile devices

## Customization

### Styling
- CSS variables for easy color scheme changes
- Responsive design with CSS Grid
- Gradient backgrounds and modern styling

### Functionality
- Modular JavaScript functions
- Event-driven architecture
- Easy to extend with additional features

### Branding
- Update header and titles
- Modify color scheme in CSS
- Add company logos or additional branding elements

## Development Workflow

1. **Test Locally**: Open `index.html` in browser
2. **API Testing**: Use browser developer tools to monitor API calls
3. **Deployment**: Upload to static hosting service
4. **Updates**: Modify files and redeploy (no build process required)

## Future Enhancements

- **User Authentication**: Add proper login system
- **Usage Analytics**: Show API usage statistics
- **Key Rotation**: Automated key rotation features
- **Team Management**: Support for organization-level access
- **Webhook Management**: Configure webhooks for events
- **SDK Downloads**: Provide client SDKs for different languages

## Support

For issues with the developer portal or API access:
1. Check the integrated documentation
2. Verify API endpoint availability
3. Review browser console for errors
4. Contact support through the ToyApi channels